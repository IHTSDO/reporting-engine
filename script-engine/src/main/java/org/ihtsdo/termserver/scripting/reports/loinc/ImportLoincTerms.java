package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.fixes.CreateConceptsPreModelled;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * LE-3
 */
public class ImportLoincTerms extends TermServerScript implements LoincConstants {
	
	protected static final String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	private static final String commonLoincColumns = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2017.tsv"  
	//-f3 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f5 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	private int FILE_IDX_LOINC_100 = 0;
	private int FILE_IDX_LOINC_100_PARTS_MAP = 1;
	private int FILE_IDX_LOINC_100_Primary = 2;
	private int FILE_IDX_LOINC_PARTS = 3;
	private int FILE_IDX_LOINC_FULL = 4;
	
	private static final String TAB_TOP_100 = "Top 100";
	private static final String TAB_TOP_2K = "Top 2K";
	private static final String TAB_PART_MAPPING_DETAIL = "Part Mapping Detail";
	private static final String TAB_RF2_PART_MAP_NOTES = "RF2 Part Map Notes";
	private static final String TAB_MODELING_NOTES = "Modeling Notes";
	private static final String TAB_PROPOSED_MODEL_COMPARISON = "Proposed Model Comparison";
	private static final String TAB_RF2_IDENTIFIER_FILE = "RF2 Identifier File";
	private static final String TAB_IMPORT_STATUS = "Import Status";
	
	private int additionalThreadCount = 0;
	
	private static String[] tabNames = new String[] {
			TAB_TOP_100,
			TAB_TOP_2K,
			TAB_PART_MAPPING_DETAIL,
			TAB_RF2_PART_MAP_NOTES,
			TAB_MODELING_NOTES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_RF2_IDENTIFIER_FILE,
			TAB_IMPORT_STATUS };
	
	private static Map<String, Concept> loincNumToSnomedConceptMap = new HashMap<>();
	private static Map<String, LoincTerm> loincNumToLoincTermMap = new HashMap<>();
	private static Map<String, RelationshipTemplate> loincPartAttributeMap = new HashMap<>();
	private Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	private Map<String, LoincPart> loincParts = new HashMap<>();
	private Map<String, Concept> categorizationMap = new HashMap<>();
	
	private Concept HasConceptCategorizationStatus;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ImportLoincTerms report = new ImportLoincTerms();
		try {
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.init(args);
			report.loadProjectSnapshot(false);
			report.postInit();
			report.runReport();
		} finally {
			report.finish();
		}
	}

	private int getTab(String tabName) throws TermServerScriptException {
		for (int i = 0; i < tabNames.length; i++) {
			if (tabNames[i].equals(tabName)) {
				return i;
			}
		}
		throw new TermServerScriptException("Tab '" + tabName + "' not recognised");
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"LoincNum, LongCommonName, Concept, Correlation, Expression," + commonLoincColumns,
				"LoincNum, LongCommonName, Concept, Correlation, Expression," + commonLoincColumns,
				"LoincNum, LoincPartNum, Advice, LoincPartName, SNOMED Attribute, ",
				"LoincPartNum, LoincPartName, PartStatus, Advice, Detail, Detail",
				"LoincNum, LoincName, Issues, ",
				"LoincNum, Existing Concept, Template, Proposed Descriptions, Current Model, Proposed Model, Difference",
				"alternateIdentifier,effectiveTime,active,moduleId,identifierSchemeId,referencedComponentId",
				"TaskId, LoincNum, Expression, Status"
		};

		super.postInit(tabNames, columnHeadings, false);
		
		knownReplacementMap.put(gl.getConcept("720309005 |Immunoglobulin G antibody to Streptococcus pneumoniae 43 (substance)|"), gl.getConcept("767402003 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 43 (substance)|"));
		knownReplacementMap.put(gl.getConcept("720308002 |Immunoglobulin G antibody to Streptococcus pneumoniae 34 (substance)|"), gl.getConcept("767408004 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 34 (substance)|"));
		knownReplacementMap.put(gl.getConcept("54708003 |Extended zinc insulin (substance)|"), gl.getConcept("10329000 |Zinc insulin (substance)|"));
		knownReplacementMap.put(gl.getConcept("409258004 |Hydroxocobalamin (substance)|"), gl.getConcept("1217427007 |Aquacobalamin (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("27192005 |Aminosalicylic acid (substance)|"), gl.getConcept("255666002 |Para-aminosalicylic acid (substance)|"));
		knownReplacementMap.put(gl.getConcept("250428009 |Substance with antimicrobial mechanism of action (substance)|"), gl.getConcept("419241000 |Substance with antibacterial mechanism of action (substance)|"));
		knownReplacementMap.put(gl.getConcept("119306004 |Drain device specimen (specimen)|"), gl.getConcept("1003707004 |Drain device submitted as specimen (specimen)|"));
	
		//Just temporarily, we need to create some concepts that aren't visible yet
		gl.registerConcept("10021010000100 |Platelet poor plasma or whole blood specimen (specimen)|"); 
		gl.registerConcept("10051010000107 |Plasma specimen or whole blood specimen (specimen)|");
		gl.registerConcept("10031010000102 |Bromocresol purple dye binding technique (qualifier value)|");
		gl.registerConcept("10041010000105 |Oximetry technique (qualifier value)|");
		gl.registerConcept("10061010000109 |Screening technique (qualifier value)|");
		
		HasConceptCategorizationStatus = gl.registerConcept("10071010000104 |Has concept categorization status (attribute)|");
		
		categorizationMap.put("Observation", gl.registerConcept("10101010000107 |Observation concept categorization status (qualifier value)|"));
		categorizationMap.put("Order", gl.registerConcept("10091010000103 |Orderable concept categorization status (qualifier value)|"));
		categorizationMap.put("Both", gl.registerConcept("10111010000105 |Both orderable and observation concept categorization status (qualifier value)|"));
		categorizationMap.put("Subset", gl.registerConcept("10121010000104 |Subset of panel concept categorization status (qualifier value)|"));
	}

	private void runReport() throws TermServerScriptException, InterruptedException {
		ExecutorService executor = Executors.newCachedThreadPool();
		populateLoincNumMap();
		loadLoincParts();
		//We can look at the full LOINC file in parallel
		executor.execute(() -> loadFullLoincFile());
		populatePartAttributeMap();
		LoincTemplatedConcept.initialise(this, gl, loincPartAttributeMap, loincNumToLoincTermMap);
		determineExistingConcepts();
		Set<LoincTemplatedConcept> successfullyModelled = doModeling();
		LoincTemplatedConcept.reportStats();
		//importIntoTask(successfullyModelled);
		//generateAlternateIdentifierFile(successfullyModelled);
		while (additionalThreadCount > 0) {
			Thread.sleep(1000);
		}
	}
	
	private void generateAlternateIdentifierFile(Set<LoincTemplatedConcept> ltcs) throws TermServerScriptException {
		for (LoincTemplatedConcept ltc : ltcs) {
			report(getTab(TAB_RF2_IDENTIFIER_FILE),
					ltc.getLoincNum(),
					today,
					"1",
					LOINC_MODULE_ID,
					SCTID_LOINC_CODE_SYSTEM,
					ltc.getConcept().getId());
		}
	}

	private void determineExistingConcepts() throws TermServerScriptException {
		try {
			info ("Loading " + getInputFile(FILE_IDX_LOINC_100));
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100)))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						LoincTerm loincTerm = LoincTerm.parse(items);
						loincNumToLoincTermMap.put(loincTerm.getLoincNum(), loincTerm);
						checkForExistingModelling(loincTerm, getTab(TAB_TOP_100));
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	
	private boolean checkForExistingModelling(LoincTerm loincTerm, int reportTab) throws TermServerScriptException {
		//Do we have this loincNum
		Concept loincConcept = loincNumToSnomedConceptMap.get(loincTerm.getLoincNum());
		if (loincConcept != null) {
			report(reportTab,
					loincTerm.getLoincNum(),
					loincTerm.getLongCommonName(),
					loincConcept, 
					LoincUtils.getCorrelation(loincConcept),
					loincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP),
					loincTerm.getCommonColumns());
			return true;
		} else {
			report(reportTab,
					loincTerm.getLoincNum(),
					loincTerm.getLongCommonName(),
					"Not Modelled",
					"",
					"",
					loincTerm.getCommonColumns());
			return false;
		}
	}

	private Set<LoincTemplatedConcept> doModeling() throws TermServerScriptException {
		Set<LoincTemplatedConcept> successfullyModelledConcepts = new HashSet<>();
		try {
			info ("Loading Parts: " + getInputFile(FILE_IDX_LOINC_100_Primary));
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100_Primary)))) {
				String line;
				ArrayList<LoincPart> loincParts = new ArrayList<>();
				String lastLoincNum = "";
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						String loincNum = items[0];
						String partNum = items[2];
						String partName = items[3];
						String partTypeName = items[5];
						
						//Have we moved on to a new LOINC Num?  Do model comparison on the last number if so,
						//which is now assumed to be complete.
						if (!lastLoincNum.equals(loincNum)) {
							if (!lastLoincNum.isEmpty()) {
								LoincTemplatedConcept templatedConcept = LoincTemplatedConcept.populateModel(lastLoincNum, loincParts);
								populateCategorization(loincNum, templatedConcept.getConcept());
								if (templatedConcept.getConcept().hasIssues()) {
									report(getTab(TAB_MODELING_NOTES),
										lastLoincNum,
										loincNumToLoincTermMap.get(lastLoincNum).getDisplayName(),
										templatedConcept.getConcept().getIssues());
								} else {
									successfullyModelledConcepts.add(templatedConcept);
									doProposedModelComparison(lastLoincNum, templatedConcept);
								}
							}
							lastLoincNum = loincNum;
							loincParts.clear();
						}
						loincParts.add(new LoincPart(partNum, partTypeName, partName));
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
		return successfullyModelledConcepts;
	}
	
	private void populateCategorization(String loincNum, Concept concept) {
		//Do we have the full set of properties for this loincTerm?
		LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
		String order = loincTerm.getOrderObs();
		Concept categoryConcept = categorizationMap.get(order);
		RelationshipTemplate rt = new RelationshipTemplate(HasConceptCategorizationStatus, categoryConcept);
		concept.addRelationship(rt, SnomedUtils.getFirstFreeGroup(concept));
	}

	private void doProposedModelComparison(String loincNum, LoincTemplatedConcept loincTemplatedConcept) throws TermServerScriptException {
		//Do we have this loincNum
		Concept existingLoincConcept = loincNumToSnomedConceptMap.get(loincNum);
		Concept proposedLoincConcept = loincTemplatedConcept.getConcept();
		
		String existingSCG = "N/A";
		String modelDiff = "";
		String proposedSCG = proposedLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String existingLoincConceptStr = "N/A";
		if (existingLoincConcept != null) {
			existingLoincConceptStr = existingLoincConcept.getId();
			existingSCG = existingLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			modelDiff = SnomedUtils.getModelDifferences(existingLoincConcept, proposedLoincConcept, CharacteristicType.STATED_RELATIONSHIP);
		}
		String proposedDescriptionsStr = SnomedUtils.prioritise(proposedLoincConcept.getDescriptions()).stream()
				.map(d -> d.toString())
				.collect(Collectors.joining(",\n"));
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON), loincNum, existingLoincConceptStr, loincTemplatedConcept.getClass().getSimpleName(), proposedDescriptionsStr, existingSCG, proposedSCG, modelDiff);
	}

	private void populateLoincNumMap() throws TermServerScriptException {
		for (Concept c : LoincUtils.getActiveLOINCconcepts(gl)) {
			loincNumToSnomedConceptMap.put(LoincUtils.getLoincNumFromDescription(c), c);
		}
		info("Populated map of " + loincNumToSnomedConceptMap.size() + " LOINC concepts");
	}
	
	private void populatePartAttributeMap() throws TermServerScriptException {
		int lineNum = 0;
		try {
			int successfullTypeReplacement = 0;
			int successfullValueReplacement = 0;
			int unsuccessfullTypeReplacement = 0;
			int unsuccessfullValueReplacement = 0;
			info ("Loading Part Attribute Map: " + getInputFile(FILE_IDX_LOINC_100_PARTS_MAP));
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100_PARTS_MAP)))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum > 1) {
						String[] items = line.split("\t");
						//Skip the row if it's not active
						if (items[2].equals("0")) {
							continue;
						}
						String partNum = items[6];
						Concept attributeType = gl.getConcept(items[7]);
						Concept attributeValue = gl.getConcept(items[5]);
						
						if (!attributeType.isActive()) {
							String hardCodedIndicator = " hardcoded";
							Concept replacementType = knownReplacementMap.get(attributeType);
							if (replacementType == null) {
								hardCodedIndicator = "";
								replacementType = getReplacementSafely(getTab(TAB_RF2_PART_MAP_NOTES), partNum, attributeType, false);
							} 
							String replacementMsg = replacementType == null ? " no replacement available." : hardCodedIndicator + " replaced with " + replacementType;
							if (replacementType == null) unsuccessfullTypeReplacement++; 
								else successfullTypeReplacement++;
							report(getTab(TAB_RF2_PART_MAP_NOTES), partNum, "Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);
							if (replacementType != null) {
								attributeType = replacementType;
							}
						}
						if (!attributeValue.isActive()) {
							String hardCodedIndicator = " hardcoded";
							Concept replacementValue = knownReplacementMap.get(attributeValue);
							if (replacementValue == null) {
								hardCodedIndicator = "";
								replacementValue = getReplacementSafely(getTab(TAB_RF2_PART_MAP_NOTES), partNum, attributeValue, false);
							}
							String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
							if (replacementValue == null) unsuccessfullValueReplacement++; 
							else successfullValueReplacement++;
							String prefix = replacementValue == null ? "* " : "";
							LoincPart part = loincParts.get(partNum);
							String partName = part == null ? "Unlisted" : part.getPartName();
							String partStatus = part == null ? "Unlisted" : part.getStatus().name();
							report(getTab(TAB_RF2_PART_MAP_NOTES), partNum, partName, partStatus, prefix + "Mapped to" + hardCodedIndicator + " inactive value: " + attributeValue + replacementMsg);
							if (replacementValue != null) {
								attributeValue = replacementValue;
							}
						}
						
						loincPartAttributeMap.put(partNum, new RelationshipTemplate(attributeType, attributeValue));
					}
				}
			}
			info("Populated map of " + loincPartAttributeMap.size() + " LOINC parts to attributes");
			report(getTab(TAB_RF2_PART_MAP_NOTES), "");
			report(getTab(TAB_RF2_PART_MAP_NOTES), "successfullTypeReplacement",successfullTypeReplacement);
			report(getTab(TAB_RF2_PART_MAP_NOTES), "unsuccessfullTypeReplacement",unsuccessfullTypeReplacement);
			report(getTab(TAB_RF2_PART_MAP_NOTES), "successfullValueReplacement",successfullValueReplacement);
			report(getTab(TAB_RF2_PART_MAP_NOTES), "unsuccessfullValueReplacement",unsuccessfullValueReplacement);

		} catch (Exception e) {
			throw new TermServerScriptException("At line " + lineNum, e);
		}
	}

	
	private void loadLoincParts() throws TermServerScriptException {
		info ("Loading Loinc Parts: " + getInputFile(FILE_IDX_LOINC_PARTS));
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
			info("Loaded " + loincParts.size() + " loinc parts.");
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load " + getInputFile(FILE_IDX_LOINC_PARTS), e);
		}
	}
	
	private void loadFullLoincFile() {
		additionalThreadCount++;
		info ("Loading Full Loinc: " + getInputFile(FILE_IDX_LOINC_FULL));
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile(FILE_IDX_LOINC_FULL)));
			//withSkipHeaderRecord() is apparently ignored when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			CSVRecord header = iterator.next();
			int existingConceptCount = 0;
			int notExistingConceptCount = 0;
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincTerm loincTerm = LoincTerm.parse(thisLine);
				//Is this term one of the top 20K?
				String testRank = loincTerm.getCommonTestRank();
				if (!StringUtils.isEmpty(testRank) && !testRank.equals("0")) {
					if (checkForExistingModelling(loincTerm, getTab(TAB_TOP_2K))) {
						existingConceptCount++;
					} else {
						notExistingConceptCount++;
					}
				}
			}
			report(getTab(TAB_TOP_2K),"");
			report(getTab(TAB_TOP_2K),"Summary:");
			report(getTab(TAB_TOP_2K),"Already exists", existingConceptCount);
			report(getTab(TAB_TOP_2K),"Does not exist", notExistingConceptCount);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load " + getInputFile(FILE_IDX_LOINC_FULL), e);
		} finally {
			additionalThreadCount--;
		}
	}
	
	private void importIntoTask(Set<LoincTemplatedConcept> successfullyModelled) throws TermServerScriptException {
		//TODO Move this class to be a BatchFix so we don't need a plug in class
		CreateConceptsPreModelled batchProcess = new CreateConceptsPreModelled(getReportManager(), getTab(TAB_IMPORT_STATUS), successfullyModelled);
		batchProcess.setProject(new Project("LE","MAIN/SNOMEDCT-LOINCEXT/LE"));
		BatchFix.headlessEnvironment = NOT_SET;  //Avoid asking any more questions to the user
		batchProcess.setServerUrl(getServerUrl());
		String[] args = new String[] { "-a", "pwilliams", "-n", "500", "-p", "LE", "-d", "N", "-c", authenticatedCookie };
		batchProcess.inflightInit(args);
		batchProcess.runJob();
	}
}
