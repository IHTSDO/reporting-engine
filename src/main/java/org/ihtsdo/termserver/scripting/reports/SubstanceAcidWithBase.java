package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Attempts to find matching acids ("x acid") and bases ("--ate")
 */
public class SubstanceAcidWithBase extends TermServerScript{
	
	String subHierarchyStr = "105590001";  // Substance (substance)
	Set<Concept> reported = new HashSet<Concept>();
	
	static final String acid = "acid";
	static final String ate = "ate";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		SubstanceAcidWithBase report = new SubstanceAcidWithBase();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			println("Generating Report");
			report.reportAcidsWithBases();
			println("Processing Complete");
		} catch (Exception e) {
			println("Failed to produce SubstanceAcidWithBase Report due to " + e.getMessage());
			e.printStackTrace();
		} 
	}

	private void reportAcidsWithBases() throws TermServerScriptException {
		Concept subHierarchy = GraphLoader.getGraphLoader().getConcept(subHierarchyStr);
		Collection<Concept> concepts = subHierarchy.getDescendents(NOT_SET);
		
		for (Concept c : concepts) {
			//Skip any we've already reported as being the matched partners
			if (reported.contains(c)) {
				continue;
			}
			boolean isBase = false;
			boolean isAcid = false;
			if (c.isActive()) {
				String term = SnomedUtils.deconstructFSN(c.getFsn())[0].toLowerCase();
				String[] parts = term.split(SPACE);
				for (String part : parts) {
					if (part.equals(acid)) {
						isAcid = true;
					}
					if (part.endsWith(ate)) {
						isBase = true;
					}
				}
				
				if (isAcid && isBase) {
					report (c, c, "Confusion");
				} else {
					if (isAcid) {
						findPartner(c, false);
					} else if (isBase) {
						findPartner(c, true);
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", concepts.size());
	}

	//Given a base or acid, searches up and down parents and descendants
	//to find the alternative form and indicates direction.
	private void findPartner(Concept c, boolean findAcid) throws TermServerScriptException {
		Set<Concept> parents = c.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false);
		boolean downstream = findAcid;
		boolean foundPartnerInParents = false;
		foundPartnerInParents = findPartner( c, parents, downstream, findAcid);
		
		boolean foundPartnerInChildren = false;
		Set<Concept> children = c.getDescendents(NOT_SET);
		downstream = !findAcid;
		foundPartnerInChildren = findPartner( c, children, downstream, findAcid);
		if (!foundPartnerInParents && !foundPartnerInChildren) {
			report (findAcid?null:c, findAcid?c:null, "Unmatched");
		}
	}

	private boolean findPartner(Concept matchMe, Set<Concept> matchSet, final boolean isDownstream, boolean findAcid) {
		boolean found = false;
		
		finished:
		for (Concept c : sort(matchSet, isDownstream)) {
			if (c.isActive()) {
				String term = SnomedUtils.deconstructFSN(c.getFsn())[0].toLowerCase();
				String[] parts = term.split(SPACE);
				for (String part : parts) {
					if (part.equals(acid) && findAcid) {
						report (c,matchMe, isDownstream?"Downstream":"Upstream");
						markReported(c);
						found = true;
						break finished;
					}
					if (part.endsWith(ate) && !findAcid) {
						report (matchMe, c, isDownstream?"Downstream":"Upstream");
						markReported(c);
						found = true;
						break finished;
					}
				}
			}
		}
		return found;
	}

	private void markReported(Concept c) {
		reported.add(c);
		if (c.getFsn().contains(acid) && c.getFsn().contains(ate)) {
			report (c, c, "Confusion");
		}
	}

	private List<Concept> sort(Set<Concept> matchSet, final boolean isDownstream) {
		//Sort the set of concepts so that we find the closest one first and stop.
		//So for upstream we sort by decreasing depth, and for downstream, by increasing
		List<Concept> matchList = new ArrayList<Concept>(matchSet);
		Collections.sort(matchList, new Comparator<Concept>() {
			public int compare(Concept o1, Concept o2) {
				Integer depth1 = o1.getDepth();
				Integer depth2 = o2.getDepth();
				if (isDownstream) {
					return depth2.compareTo(depth1);
				} else {
					return depth1.compareTo(depth2);
				}
			}
		});
		return matchList;
	}

	protected void report (Concept acid, Concept base, String notes) {
		String line =	(acid==null?"":acid.getConceptId()) + COMMA_QUOTE + 
						(acid==null?"":acid.getFsn()) + QUOTE_COMMA + 
						(base==null?"":base.getConceptId()) + COMMA_QUOTE + 
						(base==null?"":base.getFsn()) + QUOTE_COMMA_QUOTE +
						notes + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Acid_SCTID, Acid FSN, Base_SCTID, Base FSN, Notes");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
