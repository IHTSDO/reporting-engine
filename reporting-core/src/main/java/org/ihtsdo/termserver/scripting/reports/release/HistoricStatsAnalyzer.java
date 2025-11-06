package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * Input file format: sctid, active, defStatus, hierarchy, isIP, hasSdDescendant, hasSdAncestor
 * 
 * Analysis: For each major hierarchy:
 * How many concepts have been inactivated
 * How many concepts have been added
 * How many IPs have been inactivated
 * How many IPs are still there, but no longer in the position of being an IP
 * How many IPs have been fully defined
 * How many concepts have been inactivated
 * How many P concepts have gained SD ancestors, thereby becoming IPs
 * How many P concepts have gained SD descendants, thereby becoming IP
 * How many SD concepts have become P, thereby becoming IP
 * How many brand new immediately IPs have been added
  * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoricStatsAnalyzer extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricStatsAnalyzer.class);

	private static final String ITEM_LOG = "Item {}";

	private static String packageTemplate = "SnomedCT_InternationalRF2_PRODUCTION_#DATE#T120000Z.zip";
	String[] releasesToAnalyse = new String[] { "20180731", "20230331" };
	
	Map<String, Map<Long, Datum>> prevData;
	Map<String, Map<Long, Datum>> thisData;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(HistoricStatsAnalyzer.class, args, params);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Historic Stats Analyser")
				.withDescription("Compares successive releases of SNOMED CT - with a particular focus on Intermediate Primitives")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withTag(INT)
				.build();
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String standardHeading = "Hierarchy, FSN, SemTag, Active Start, Concepts Added, Concepts Inactivated, " +
				"P made SD, SD made P, SD Inactivated, SD Added, CHECK_ALIGNMENT," +
				"IPs Start, IPs Removed Total, IPs Added Total, IPs Finish, IPs Brand New, IPs Inactivated, " + 
				"IPs made SD, IPs No Longer - lost SD ancestor, IPs No Longer - lost SD descendant," +
				"IPs No Longer - lost either, CHECK_ALIGNMENT, " +
				"New IPs gained SD descendant, New IPs gained SD ancestor, New IPs gained SD either, New IP SD->P";
		int tabCount = (releasesToAnalyse.length - 1) * 2;
		String percHeading = "Hierarchy, FSN, SemTag, SD Start, SD Finish, P Start, P Finish, IPs Start, IPs Finish";
		String[] columnHeadings = new String[tabCount]; 
		String[] tabNames = new String[tabCount];
		for (int i=1; i < tabCount; i = i + 2) {
			columnHeadings[i-1] = standardHeading;
			columnHeadings[i] = percHeading;
			tabNames[i-1] = releasesToAnalyse[i-1] + " - " + releasesToAnalyse[i];
			tabNames[i] = releasesToAnalyse[i-1] + " - " + releasesToAnalyse[i] + " Perc";
		}
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (int i = 0; i < releasesToAnalyse.length ; i++) {
			loadData(releasesToAnalyse[i]);
			if (prevData != null) {
				for (String hierarchyStr : thisData.keySet()) {
					if (!StringUtils.isNumeric(hierarchyStr)) {
						LOGGER.debug("here");
					}
					LOGGER.info("Analysing data from " + releasesToAnalyse[i] + " hierarchy " + gl.getConcept(hierarchyStr));
					runAnalysis((i - 1) * 2, hierarchyStr);
					runPercAnalysis(((i -1) * 2) + 1, hierarchyStr);
				}
			}
			prevData = thisData;
		}
	}

	private void loadData(String release) throws TermServerScriptException {
		File dataFile = null;
		thisData = new HashMap<>();
		try {
			dataFile = new File("historic-data/" + release + ".tsv");
			if (!dataFile.exists() || !dataFile.canRead()) {
				//Try using the date
				String fileName = dataFile.getPath().replace(release, packageTemplate).replace("#DATE#", release); 
				dataFile = new File(fileName);
				if (!dataFile.exists() || !dataFile.canRead()) {
					throw new TermServerScriptException("Unable to load historic data: " + dataFile + " nor " + release + ".tsv");
				}
			}
			LOGGER.info("Loading {}", dataFile);
			BufferedReader br = new BufferedReader(new FileReader(dataFile));
			int lineNumber = 0;
			String line = "";
			try {
				while ((line = br.readLine()) != null) {
					lineNumber++;
					Datum datum = fromLine(line);
					//Does this datum know its hierarchy?  If not, we might find it in the previous release
					if (datum.hierarchy.isEmpty() && prevData != null) {
						Datum prevDatum = getConcept(datum.conceptId, prevData);
						datum.hierarchy = prevDatum.hierarchy;
					} 
					
					if (datum.hierarchy.isEmpty()){
						datum.hierarchy = "54690008 |Unknown (origin) (qualifier value)|";
					}
					
					//Have we seen this hierarchy before?
					Map<Long, Datum> thisHierarchy = thisData.get(datum.hierarchy);
					if (thisHierarchy == null) {
						thisHierarchy = new HashMap<>();
						thisData.put(datum.hierarchy, thisHierarchy);
					}
					thisHierarchy.put(datum.conceptId, datum);
				}
			} catch (Exception e) {
				String err = e.getClass().getSimpleName();
				throw new TermServerScriptException(err + " at line " + lineNumber + ": " + line);
			} finally {
				br.close();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load " + dataFile, e);
		}
	}
	
	private void runAnalysis(int tabIdx, final String hierarchyStr) throws TermServerScriptException {
		Map<Long, Datum> thisHierarchy = thisData.get(hierarchyStr);
		Map<Long, Datum> prevHierarchy = prevData.get(hierarchyStr);
		Object[] results = new Object[23];
		int column = 0;
		
		//Sanity check here that we've no SD IPs
		long sanityCheck = thisHierarchy.values().stream()
				.filter(d -> d.isIP && d.isSD)
				.count();
		if (sanityCheck > 0) {
			throw new IllegalArgumentException("Data has an SD IP!");
		}
		
		//Also can't have any inactive SD
		sanityCheck = thisHierarchy.values().stream()
				.filter(d -> !d.isActive && d.isSD)
				.count();
		if (sanityCheck > 0) {
			throw new IllegalArgumentException("Data has an inactive SD");
		}
		
		//Also can't have any IPs that are missing SDs above or below
		sanityCheck = thisHierarchy.values().stream()
				.filter(d -> d.isIP && (!d.hasSdAncestor || !d.hasSdDescendant))
				.count();
		if (sanityCheck > 0) {
			throw new IllegalArgumentException("Data has IP without SD above or below");
		}

		//1. What's our active start count?
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream().filter(d -> d.isActive).count();
		
		//2. Concepts added
		LOGGER.debug(ITEM_LOG, (column + 1));
		Set<Datum> newConcepts = new HashSet<>(thisHierarchy.values());
		Set<Datum> existingConcepts = new HashSet<>(prevHierarchy.values());
		LOGGER.debug("data cloned");
		newConcepts.removeAll(existingConcepts);
		results[column++] = (long) newConcepts.size();
		
		//3. Concepts Inactivated.  Find concepts in prev that are active, where this concept
		//is not active
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isActive && !getConcept(d.conceptId, hierarchyStr, thisData).isActive)
				.count();
		
		//4. P made SD.  Find concepts in prev release that are not SD, that are SD in this release
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> !d.isSD && getConcept(d.conceptId, hierarchyStr, thisData).isSD)
				.count();
		
		//5. SD made P.  Find concepts in prev release that are  SD, that are not SD in this release
		//And also the the concept is still active
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isSD && 
						!getConcept(d.conceptId, hierarchyStr, thisData).isSD && 
						getConcept(d.conceptId, hierarchyStr, thisData).isActive)
				.count();
						
		//6. SD Inactivated.  Find concepts in prev release that are  SD, that are now inactive
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isSD && !getConcept(d.conceptId, hierarchyStr, thisData).isActive)
				.count();
		
		//7. SD Added
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = newConcepts.stream().filter(d -> d.isSD).count();
		
		//8. Check alignement
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = "Check";
		
		//9. How many IPs do we have to start with?
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream().filter(d -> d.isIP).count();
		
		//10. IPs removed total
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isIP && !getConcept(d.conceptId, hierarchyStr, thisData).isIP)
				.count();
		
		//11. IPs Added total. All current IPs that either did not exist at all, or 
		//were not IPs
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = thisHierarchy.values().stream()
				.filter(d -> d.isIP && 
						( getConcept(d.conceptId, hierarchyStr, prevData) == null ||
								!getConcept(d.conceptId, hierarchyStr, prevData).isIP))
				.count();
		
		//12. How many IPs do we finish up with?
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = thisHierarchy.values().stream().filter(d -> d.isIP).count();
		
		//13. IPs brand new.  Use our collection of new concepts to count these
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = newConcepts.stream().filter(d -> d.isIP).count();
		
		//14. IPs inactivated.  IPs in the prev release that are now inactive
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isIP && !getConcept(d.conceptId, hierarchyStr, thisData).isActive)
				.count();
		
		//15. IPs made SD
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isIP 
						&& getConcept(d.conceptId, hierarchyStr, thisData).isSD)
				.count();
		
		//16. IPs No Longer - lost SD ancestor. So still active, but no SD ancestor
		//Also check that it's still primitive, else it will have been counted elsewhere
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isIP && !getConcept(d.conceptId, hierarchyStr, thisData).isIP
						&& !getConcept(d.conceptId, hierarchyStr, thisData).hasSdAncestor
						&& getConcept(d.conceptId, hierarchyStr, thisData).isActive
						&& !getConcept(d.conceptId, hierarchyStr, thisData).isSD)
				.count();
		
		//17. IPs No Longer - lost SD descendant
		//Also check that it's still primitive, else it will have been counted elsewhere
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isIP && !getConcept(d.conceptId, hierarchyStr, thisData).isIP
						&& !getConcept(d.conceptId, hierarchyStr, thisData).hasSdDescendant
						&& getConcept(d.conceptId, hierarchyStr, thisData).isActive
						&& !getConcept(d.conceptId, hierarchyStr, thisData).isSD)
				.count();
		
		//18. IPs No Longer - lost either
		//Also check that it's still primitive, else it will have been counted elsewhere
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isIP && !getConcept(d.conceptId, hierarchyStr, thisData).isIP
						&& (!getConcept(d.conceptId, hierarchyStr, thisData).hasSdAncestor
						|| !getConcept(d.conceptId, hierarchyStr, thisData).hasSdDescendant)
						&& getConcept(d.conceptId, hierarchyStr, thisData).isActive
						&& !getConcept(d.conceptId, hierarchyStr, thisData).isSD)
				.count();
		
		//19. Check alignement
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = "Check";
		
		//For these next few, filter out the cases where it was previously not an IP
		//because it was SD.  We'll count them at the end.
		
		//20. New IPs gained SD descendant. So without SD descendant and is now IP 
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> !d.isIP && !d.isSD
						&& !d.hasSdDescendant 
						&& getConcept(d.conceptId, hierarchyStr, thisData).isIP)
				.count();
		
		//21. New IPs gained SD ancestor, So without SD ancestor and is now IP
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> !d.isIP  && !d.isSD
						&& !d.hasSdAncestor
						&& getConcept(d.conceptId, hierarchyStr, thisData).isIP)
				.count();
		
		//22. New IPs gained SD either
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> !d.isIP && !d.isSD 
						&& (!d.hasSdAncestor || !d.hasSdDescendant) 
						&& getConcept(d.conceptId, hierarchyStr, thisData).isIP)
				.count();
		
		//23. New IPs switched from SD to P
		LOGGER.debug(ITEM_LOG, (column + 1));
		results[column++] = prevHierarchy.values().stream()
				.filter(d -> d.isSD &&
						getConcept(d.conceptId, hierarchyStr, thisData).isIP
						&& !getConcept(d.conceptId, hierarchyStr, thisData).isSD)
				.count();
		
		Concept hierarchy;
		if (StringUtils.isNumeric(hierarchyStr) && hierarchyStr.length() > 6) {
			hierarchy = gl.getConcept(hierarchyStr);
		} else {
			hierarchy = new Concept(hierarchyStr);
		}
		LOGGER.debug("Outputting data");
		report(tabIdx, hierarchy, results);
	}
	
	private void runPercAnalysis(int tabIdx, final String hierarchyStr) throws TermServerScriptException {
		Map<Long, Datum> thisHierarchy = thisData.get(hierarchyStr);
		Map<Long, Datum> prevHierarchy = prevData.get(hierarchyStr);
		Object[] results = new Object[6];
		int column = 0;

		//What's our active counts?
		long prevActiveCount = prevHierarchy.values().stream().filter(d -> d.isActive).count();
		long thisActiveCount = thisHierarchy.values().stream().filter(d -> d.isActive).count();
		
		if (prevActiveCount == 0 || thisActiveCount == 0) {
			Arrays.fill(results, "N/A");
		} else {
			//Percentage of SD 
			long prevSDCount = prevHierarchy.values().stream().filter(d -> d.isSD && d.isActive).count();
			results[column++] = String.format("%.1f%%", (prevSDCount / (double)prevActiveCount) * 100);
		
			long thisSDCount = thisHierarchy.values().stream().filter(d -> d.isSD && d.isActive).count();
			results[column++] = String.format("%.1f%%", (thisSDCount / (double)thisActiveCount) * 100);
			
			//Percentage of P
			long prevPCount = prevHierarchy.values().stream().filter(d -> !d.isSD && d.isActive).count();
			results[column++] = String.format("%.1f%%", (prevPCount / (double)prevActiveCount) * 100);
		
			long thisPCount = thisHierarchy.values().stream().filter(d -> !d.isSD && d.isActive).count();
			results[column++] = String.format("%.1f%%", (thisPCount / (double)thisActiveCount) * 100);
			
			//Percentage of IPs
			long prevIPCount = prevHierarchy.values().stream().filter(d -> d.isIP && d.isActive).count();
			results[column++] = String.format("%.1f%%", (prevIPCount / (double)prevActiveCount) * 100);
		
			long thisIPCount = thisHierarchy.values().stream().filter(d -> d.isIP && d.isActive).count();
			results[column++] = String.format("%.1f%%", (thisIPCount / (double)thisActiveCount) * 100);

		}
		
		Concept hierarchy;
		if (StringUtils.isNumeric(hierarchyStr) && hierarchyStr.length() > 6) {
			hierarchy = gl.getConcept(hierarchyStr);
		} else {
			hierarchy = new Concept(hierarchyStr);
		}
		LOGGER.debug("Outputting data");
		report(tabIdx, hierarchy, results);
	}

	private Datum getConcept(long conceptId, Map<String, Map<Long, Datum>> data) {
		for (Map<Long, Datum> thisHierarchy : data.values()) {
			if (thisHierarchy.containsKey(conceptId)) {
				return thisHierarchy.get(conceptId);
			}
		}
		return null;
	}
	
	private Datum getConcept(long conceptId, String hierarchyStr, Map<String, Map<Long, Datum>> data) {
		Map<Long, Datum> thisHierarchy = data.get(hierarchyStr);
		if (thisHierarchy == null) {
			throw new IllegalArgumentException("No sign of " + hierarchyStr + " hierarchy in data");
		}
		if (thisHierarchy.containsKey(conceptId)) {
			if (thisHierarchy.get(conceptId) == null) {
				throw new IllegalArgumentException("Null datum stored?");
			}
			return thisHierarchy.get(conceptId);
		} else {
			//Is it possible that the concept has moved top level hierarchy?
			for (String otherHierarchyStr : data.keySet()) {
				Map<Long, Datum> thisOtherHierarchy = data.get(otherHierarchyStr);
				if (thisOtherHierarchy.containsKey(conceptId)) {
					Datum datum = thisOtherHierarchy.get(conceptId);
					//If we thought it was in the unknown hierarchy and it's not, 
					//that's fine, it's been reactivated
					if (datum != null && !hierarchyStr.contains("Unknown")) {
						try {
							LOGGER.warn (conceptId + " expected in " + gl.getConcept(hierarchyStr) + " but found in " + gl.getConcept(datum.hierarchy));
						} catch (TermServerScriptException e) {
							LOGGER.error("Exception encountered",e);
						}
					}
					return datum;
				}
			}
			if (data != prevData) {
				LOGGER.warn ("Concept {} not found in any subHierarchy", conceptId);
			}
		}
		return null;
	}

	protected class Datum {
		long conceptId;
		boolean isActive;
		boolean isSD;
		String hierarchy;
		boolean isIP;
		boolean hasSdDescendant;
		boolean hasSdAncestor;
		int hashCode;
		
		@Override
		public int hashCode () {
			return hashCode;
		}
		
		@Override
		public boolean equals (Object o) {
			if (o instanceof Datum datum) {
				return this.conceptId == datum.conceptId;
			}
			return false;
		}
	}
	
	Datum fromLine (String line) {
		Datum datum = new Datum();
		String[] lineItems = line.split(TAB);
		int idx = 0;
		datum.conceptId = Long.parseLong(lineItems[idx]);
		++idx; //Don't need the FSN
		datum.isActive = lineItems[++idx].equals("Y");
		datum.isSD = lineItems[++idx].equals("SD");
		datum.hierarchy = lineItems[++idx];
		datum.isIP = lineItems[++idx].equals("Y");
		datum.hasSdAncestor = lineItems[++idx].equals("Y");
		datum.hasSdDescendant = lineItems[++idx].equals("Y");
		datum.hashCode = Long.hashCode(datum.conceptId);
		return datum;
	}
}
