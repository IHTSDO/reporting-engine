package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincPart;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

public abstract class LoincScript extends ContentPipelineManager implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincScript.class);

	protected Set<String> panelLoincNums = new HashSet<>();
	
	protected static Set<String> objectionableWords = new HashSet<>(Arrays.asList("panel"));

	//Map of LoincNums to ldtColumnNames to details
	protected static Map<String, Map<String, LoincDetail>> loincDetailMapOfMaps = new HashMap<>();

	public static final String LOINC_TIME_PART = "LP6969-2";
	public static final String LOINC_OBSERVATION_PART = "LP442509-8";

	private static final List<String> ALLOW_ABSENT_MAPPING = Arrays.asList(LOINC_TIME_PART, LOINC_OBSERVATION_PART);
	
	protected String[] getTabNames() {
		throw new IllegalStateException("Please override getTabNames() in your script");
	}

	public void postInit(String[] tabNames, String[] columnHeadings) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1yF2g_YsNBepOukAu2vO0PICqJMAyURwh");  //LOINC Folder
		tabForFinalWords = SECONDARY_REPORT;
		postInit(GFOLDER_LOINC, tabNames, columnHeadings, false);
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		loadFullLoincFile(NOT_SET);
		loadLoincParts();
	}

	@Override
	protected String getContentType() {
		return "Observable";
	}

	
	protected void loadLoincDetail() throws TermServerScriptException {
		LOGGER.info("Loading Loinc Detail: {}", getInputFile(FILE_IDX_LOINC_DETAIL));
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_DETAIL)));
			String line = in.readLine();
			int count = 0;
			while (line != null) {
				if (count > 0) {
					LoincDetail loincDetail = LoincDetail.parse(line.split(TAB));
					//Have we seen anything details for this LoincNum?
					Map<String, LoincDetail> partDetailMap = loincDetailMapOfMaps.get(loincDetail.getLoincNum());
					if (partDetailMap == null) {
						partDetailMap = new HashMap<>();
						loincDetailMapOfMaps.put(loincDetail.getLoincNum(), partDetailMap);
					}
					//Now have we seen anything for this loincPart before?
					if (partDetailMap.containsKey(loincDetail.getLDTColumnName())) {
						throw new TermServerScriptException("Duplicate LOINC detail encountered in " + FILE_IDX_LOINC_DETAIL + ": " + loincDetail);
					}
					partDetailMap.put(loincDetail.getLDTColumnName(), loincDetail);
				}
				count++;
				line = in.readLine();
			}
			LOGGER.info("Loaded {} details for {} loincNums", count, loincDetailMapOfMaps.size());
		} catch (Exception e) {
			throw new TermServerScriptException(FAILED_TO_LOAD + getInputFile(FILE_IDX_LOINC_DETAIL), e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					//Don't worry about a failure to close the file, we'll survive
				}
			}
		}
	}

	protected void loadPanels() throws TermServerScriptException {
		LOGGER.info("Loading Loinc Panels: {}", getInputFile(FILE_IDX_PANELS));
		int lineCount = 0;
		try (BufferedReader in = new BufferedReader(new FileReader(getInputFile(FILE_IDX_PANELS)))){
			String line = in.readLine();
			while (line != null) {
				if (lineCount > 0) {
					panelLoincNums.add(line.split(TAB)[5]);
				}
				lineCount++;
				line = in.readLine();
			}
			LOGGER.info("Loaded {} LOINC panels", panelLoincNums.size());
		} catch (Exception e) {
			throw new TermServerScriptException(FAILED_TO_LOAD + getInputFile(FILE_IDX_PANELS) + " at line " + lineCount, e);
		}
	}
	
	protected void loadLoincParts() throws TermServerScriptException {
		LOGGER.info("Loading Loinc Parts: {}", getInputFile(FILE_IDX_LOINC_PARTS));
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile(FILE_IDX_LOINC_PARTS)));
			//withSkipHeaderRecord() is apparently ignored when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			iterator.next(); //Throw away the header row
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincPart loincPart = LoincPart.parse(thisLine);
				if (partMap.containsKey(loincPart.getPartNumber())) {
					throw new TermServerScriptException("Duplicate Part Number Loaded: " + loincPart.getPartNumber());
				}
				partMap.put(loincPart.getPartNumber(), loincPart);
			}
			LOGGER.info("Loaded {} loinc parts.", partMap.size());
		} catch (Exception e) {
			throw new TermServerScriptException(FAILED_TO_LOAD + getInputFile(FILE_IDX_LOINC_PARTS), e);
		}
	}

	protected void loadFullLoincFile(int tabIdx) throws TermServerScriptException {
		loadFullLoincFile(tabIdx, getInputFile(FILE_IDX_LOINC_FULL));
	}

	protected void loadFullLoincFile(int tabIdx, File fullLoincFile) throws TermServerScriptException {
		additionalThreadCount++;
		LOGGER.info("Loading Full Loinc: {}", fullLoincFile);
		Set<String> targettedProperties = new HashSet<>(Arrays.asList("PrThr", "MCnc","ACnc", "SCnc","Titr", "Prid"));
		try {
			Reader in = new InputStreamReader(new FileInputStream(fullLoincFile));
			//withSkipHeaderRecord() is apparently ignored when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			iterator.next();  //throw away the header row
			int hasTargettedPropertyIn20K = 0;
			int hasTargettedPropertyNotIn20K = 0;
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincTerm loincTerm = LoincTerm.parse(thisLine);
				externalConceptMap.put(loincTerm.getLoincNum(), loincTerm);
				//Is this term one of the top 20K?
				String testRank = loincTerm.getCommonTestRank();
				if (StringUtils.isEmpty(testRank) || testRank.equals("0")) {
					if (targettedProperties.contains(loincTerm.getProperty())) {
						hasTargettedPropertyNotIn20K++;
					}
				} else if (tabIdx != NOT_SET && targettedProperties.contains(loincTerm.getProperty())) {
					hasTargettedPropertyIn20K++;
				}
			}
			if (tabIdx != NOT_SET) {
				report(tabIdx,"");
				report(tabIdx,"Summary:");
				report(tabIdx,"");
				report(tabIdx,"Has Targeted Property in Top 20K", hasTargettedPropertyIn20K);
				report(tabIdx,"Has Targeted Property not in Top 20K", hasTargettedPropertyNotIn20K);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(FAILED_TO_LOAD + fullLoincFile, e);
		} finally {
			additionalThreadCount--;
		}
	}

	protected void addPartMapNote(String partNum, String note) {
		if (partMapNotes.containsKey(partNum)) {
			partMapNotes.put(partNum, partMapNotes.get(partNum) + ", " + note);
		} else {
			partMapNotes.put(partNum, note);
		}
	}

	protected void addPartMapNotes(String partNum , List<String> notes) {
		for (String note : notes) {
			addPartMapNote(partNum, note);
		}
	}

	protected String getPartMapNotes(String partNum) {
		return partMapNotes.containsKey(partNum) ? partMapNotes.get(partNum) : "";
	}

	public LoincTerm getLoincTerm(String loincNum) {
		return (LoincTerm)externalConceptMap.get(loincNum);
	}
	
	public LoincPart getLoincPart(String loincPartNum) {
		return (LoincPart)partMap.get(loincPartNum);
	}

	public List<String> getMappingsAllowedAbsent() {
		return LoincScript.ALLOW_ABSENT_MAPPING;
	}
	
	@Override
	protected String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts) {
		//Return an array containing:
		//High Usage (Y/N) if any of these terms are high usage (top 20K)
		//Highest Usage (Y/N) if any of these terms are highest usage (top 2K)
		//Top Priority Usage
		//Highest Rank
		//Highest Usage Count - how many of these items are in the highest usage
		Set<LoincTerm> loincTerms = externalConcepts.stream()
				.map(e -> (LoincTerm)e)
				.collect(Collectors.toSet());
		
		String[] highUsageIndicators = new String[]{"N", "N", "", "", ""};
		int highestUsageCount = 0;
		for (LoincTerm loincTerm : loincTerms) {
			if (loincTerm.isHighUsage()) {
				highUsageIndicators[0] = "Y";
			}
			if (loincTerm.isHighestUsage()) {
				highUsageIndicators[1] = "Y";
				highestUsageCount++;
			}
		}

		highUsageIndicators[2] = loincTerms.stream()
				.sorted()
				.map(lt -> "(" + lt.getCommonTestRank() + ") " + lt.getLoincNum() + ": " + lt.getLongDisplayName() )
				.limit(5)
				.collect(Collectors.joining(",\n"));

		highUsageIndicators[3] = loincTerms.stream()
				.sorted()
				.map(LoincTerm::getCommonTestRank)
				.findFirst().orElse("0");

		highUsageIndicators[4] = Integer.toString(highestUsageCount);

		return highUsageIndicators;
	}
	
	@Override
	protected Set<String> getObjectionableWords() {
		return objectionableWords;
	}

	protected void stop() {
		throw new IllegalStateException("This class is not expected to do any modelling");
	}
}
