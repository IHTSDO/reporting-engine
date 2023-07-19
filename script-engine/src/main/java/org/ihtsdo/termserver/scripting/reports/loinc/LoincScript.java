package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.script.dao.ReportSheetManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincScript extends TermServerScript implements LoincConstants {

	private static Logger LOGGER = LoggerFactory.getLogger(LoincScript.class);

	protected static Map<String, Concept> loincNumToSnomedConceptMap = new HashMap<>();
	protected static Map<String, LoincTerm> loincNumToLoincTermMap = new HashMap<>();
	protected static AttributePartMapManager attributePartMapManager;
	protected Map<String, LoincPart> loincParts = new HashMap<>();
	protected Map<String, Concept> categorizationMap = new HashMap<>();
	
	//Map of LoincNums to ldtColumnNames to details
	protected static Map<String, Map<String, LoincDetail>> loincDetailMap = new HashMap<>();
	
	//private Concept HasConceptCategorizationStatus;
	
	protected static int FILE_IDX_LOINC_100 = 0;
	protected static int FILE_IDX_LOINC_PARTS_MAP_BASE_FILE = 1;
	//private static int FILE_IDX_LOINC_100_Primary = 2;
	protected static int FILE_IDX_LOINC_PARTS = 3;
	protected static int FILE_IDX_LOINC_FULL = 4;
	protected static int FILE_IDX_LOINC_DETAIL = 5;
	protected static int FILE_IDX_CONCEPT_IDS = 6;
	protected static int FILE_IDX_DESC_IDS = 7;
	
	protected int additionalThreadCount = 0;
	
	public void postInit(String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh";  //LOINC Folder
		
		//Just temporarily, we need to create some concepts that aren't visible yet
		gl.registerConcept("10021010000100 |Platelet poor plasma or whole blood specimen (specimen)|"); 
		gl.registerConcept("10051010000107 |Plasma specimen or whole blood specimen (specimen)|");
		gl.registerConcept("10031010000102 |Bromocresol purple dye binding technique (qualifier value)|");
		gl.registerConcept("10041010000105 |Oximetry technique (qualifier value)|");
		gl.registerConcept("10061010000109 |Screening technique (qualifier value)|");
		
		super.postInit(tabNames, columnHeadings, csvOutput);
	}
	
	protected void loadLoincDetail() {
		LOGGER.info ("Loading Loinc Detail: " + getInputFile(FILE_IDX_LOINC_DETAIL));
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_DETAIL)));
			String line = in.readLine();
			int count = 0;
			while (line != null) {
				if (count > 0) {
					LoincDetail loincDetail = LoincDetail.parse(line.split(TAB));
					//Have we seen anything details for this LoincNum?
					Map<String, LoincDetail> partDetailMap = loincDetailMap.get(loincDetail.getLoincNum());
					if (partDetailMap == null) {
						partDetailMap = new HashMap<>();
						loincDetailMap.put(loincDetail.getLoincNum(), partDetailMap);
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
			LOGGER.info("Loaded " + count + " details for " + loincDetailMap.size() + " loincNums");
		} catch (Exception e) {
			throw new RuntimeException("Failed to load " + getInputFile(FILE_IDX_LOINC_DETAIL), e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {}
			}
		}
	}
	
	protected void loadLoincParts() throws TermServerScriptException {
		LOGGER.info ("Loading Loinc Parts: " + getInputFile(FILE_IDX_LOINC_PARTS));
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile(FILE_IDX_LOINC_PARTS)));
			//withSkipHeaderRecord() is apparently ignored when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			CSVRecord header = iterator.next();
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincPart loincPart = LoincPart.parse(thisLine);
				if (loincParts.containsKey(loincPart.getPartNumber())) {
					throw new TermServerScriptException("Duplicate Part Number Loaded: " + loincPart.getPartNumber());
				}
				loincParts.put(loincPart.getPartNumber(), loincPart);
			}
			LOGGER.info("Loaded " + loincParts.size() + " loinc parts.");
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load " + getInputFile(FILE_IDX_LOINC_PARTS), e);
		}
	}
	
/*	protected void determineExistingConcepts(int tabIdx) throws TermServerScriptException {
		int total  = 0;
		int existingConceptCount = 0;
		try {
			LOGGER.info ("Loading " + getInputFile(FILE_IDX_LOINC_100));
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100)))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						LoincTerm loincTerm = LoincTerm.parse(items);
						loincNumToLoincTermMap.put(loincTerm.getLoincNum(), loincTerm);
						boolean exists = checkForExistingModelling(loincTerm, tabIdx);
						if (exists) existingConceptCount++;
					} else isFirstLine = false;
				}
			}
			report(tabIdx,"");
			report(tabIdx,"Summary:");
			report(tabIdx,"Already exists", existingConceptCount);
			report(tabIdx,"Total", total);
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}*/
	
	private boolean checkForExistingModelling(LoincTerm loincTerm, int tabIdx) throws TermServerScriptException {
		//Do we have this loincNum
		Concept loincConcept = loincNumToSnomedConceptMap.get(loincTerm.getLoincNum());
		if (loincConcept != null) {
			report(tabIdx,
					loincTerm.getLoincNum(),
					loincTerm.getLongCommonName(),
					loincConcept, 
					LoincUtils.getCorrelation(loincConcept),
					loincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP),
					loincTerm.getCommonColumns());
			return true;
		} else {
			report(tabIdx,
					loincTerm.getLoincNum(),
					loincTerm.getLongCommonName(),
					"Not Modelled",
					"",
					"",
					loincTerm.getCommonColumns());
			return false;
		}
	}
	
	protected void loadFullLoincFile(int tabIdx) {
		additionalThreadCount++;
		LOGGER.info ("Loading Full Loinc: " + getInputFile(FILE_IDX_LOINC_FULL));
		loincNumToLoincTermMap = new HashMap<>();
		Set<String> targettedProperties = new HashSet<>(Arrays.asList("PrThr", "MCnc","ACnc", "SCnc","Titr", "Prid"));
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile(FILE_IDX_LOINC_FULL)));
			//withSkipHeaderRecord() is apparently ignored when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			CSVRecord header = iterator.next();
			int existingConceptCount = 0;
			int notExistingConceptCount = 0;
			int hasTargettedPropertyIn20K = 0;
			int hasTargettedPropertyNotIn20K = 0;
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincTerm loincTerm = LoincTerm.parse(thisLine);
				loincNumToLoincTermMap.put(loincTerm.getLoincNum(), loincTerm);
				//Is this term one of the top 20K?
				String testRank = loincTerm.getCommonTestRank();
				if (StringUtils.isEmpty(testRank) || testRank.equals("0")) {
					if (targettedProperties.contains(loincTerm.getProperty())) {
						hasTargettedPropertyNotIn20K++;
					}
				} else if (tabIdx != NOT_SET){
					if (checkForExistingModelling(loincTerm, tabIdx)) {
						existingConceptCount++;
					} else {
						notExistingConceptCount++;
					}
					
					if (targettedProperties.contains(loincTerm.getProperty())) {
						hasTargettedPropertyIn20K++;
					}
				}
			}
			if (tabIdx != NOT_SET) {
				report(tabIdx,"");
				report(tabIdx,"Summary:");
				report(tabIdx,"Already exists", existingConceptCount);
				report(tabIdx,"Does not exist", notExistingConceptCount);
				report(tabIdx,"");
				report(tabIdx,"Has Targetted Property in Top 20K", hasTargettedPropertyIn20K);
				report(tabIdx,"Has Targetted Property not in Top 20K", hasTargettedPropertyNotIn20K);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to load " + getInputFile(FILE_IDX_LOINC_FULL), e);
		} finally {
			additionalThreadCount--;
		}
	}
}
