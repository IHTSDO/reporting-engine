package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.fixes.CreateConceptsPreModelled;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * LE-3
 */
public class ImportLoincTerms extends LoincScript {
	
	protected static final String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	private static final String commonLoincColumns = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f1 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2023.tsv"  
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f3 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	//-f5 "G:\My Drive\018_Loinc\2023\Loinc_Detail_Type_1_All_Active_Lab_Parts.xlsx - LDT1_Parts.tsv" 
	
	public static final String TAB_TOP_100 = "Top 100";
	public static final String TAB_TOP_20K = "Top 20K";
	public static final String TAB_PART_MAPPING_DETAIL = "Part Mapping Detail";
	public static final String TAB_RF2_PART_MAP_NOTES = "RF2 Part Map Notes";
	public static final String TAB_MODELING_ISSUES = "Modeling Issues";
	public static final String TAB_PROPOSED_MODEL_COMPARISON = "Proposed Model Comparison";
	public static final String TAB_MAP_ME = "Map Me!";
	public static final String TAB_RF2_IDENTIFIER_FILE = "RF2 Identifier File";
	public static final String TAB_IMPORT_STATUS = "Import Status";
	
	private static String[] tabNames = new String[] {
			TAB_TOP_100,
			TAB_TOP_20K,
			TAB_PART_MAPPING_DETAIL,
			TAB_RF2_PART_MAP_NOTES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_RF2_IDENTIFIER_FILE,
			TAB_IMPORT_STATUS };
	
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
			while (report.additionalThreadCount > 0) {
				Thread.sleep(1000);
			}
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
				"LoincNum, Existing Concept, Template, Proposed Descriptions, Current Model, Proposed Model, Difference,"  + commonLoincColumns,
				"PartNum, PartName, PriorityIndex, Usage Count, Top Priority Usage, ",
				"alternateIdentifier,effectiveTime,active,moduleId,identifierSchemeId,referencedComponentId",
				"TaskId, Concept, Severity, Action, LoincNum, Expression, Status, , "
		};

		super.postInit(tabNames, columnHeadings, false);
		
		//HasConceptCategorizationStatus = gl.registerConcept("10071010000104 |Has concept categorization status (attribute)|");
		categorizationMap.put("Observation", gl.registerConcept("10101010000107 |Observation concept categorization status (qualifier value)|"));
		categorizationMap.put("Order", gl.registerConcept("10091010000103 |Orderable concept categorization status (qualifier value)|"));
		categorizationMap.put("Both", gl.registerConcept("10111010000105 |Both orderable and observation concept categorization status (qualifier value)|"));
		categorizationMap.put("Subset", gl.registerConcept("10121010000104 |Subset of panel concept categorization status (qualifier value)|"));
	}

	private void runReport() throws TermServerScriptException, InterruptedException {
		//ExecutorService executor = Executors.newCachedThreadPool();
		populateLoincNumMap();
		loadLoincParts();
		//We can look at the full LOINC file in parallel as it's not needed for modelling
		//executor.execute(() -> loadFullLoincFile());
		loadFullLoincFile(getTab(TAB_TOP_20K));
		loadLoincDetail();
		attributePartMapManager = new AttributePartMapManager(this, loincParts);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_LOINC_100_PARTS_MAP));
		LoincTemplatedConcept.initialise(this, gl, attributePartMapManager, loincNumToLoincTermMap, loincDetailMap, loincParts);
		determineExistingConcepts(getTab(TAB_TOP_100));
		Set<LoincTemplatedConcept> successfullyModelled = doModeling();
		LoincTemplatedConcept.reportStats();
		LoincTemplatedConcept.reportFailedMappingsByProperty(getTab(TAB_MODELING_ISSUES));
		LoincTemplatedConcept.reportMissingMappings(getTab(TAB_MAP_ME));
		/*importIntoTask(successfullyModelled);
		generateAlternateIdentifierFile(successfullyModelled);*/
	}
	
	private void generateAlternateIdentifierFile(Set<LoincTemplatedConcept> ltcs) throws TermServerScriptException {
		for (LoincTemplatedConcept ltc : ltcs) {
			report(getTab(TAB_RF2_IDENTIFIER_FILE),
					ltc.getLoincNum(),
					today,
					"1",
					SCTID_LOINC_EXTENSION_MODULE,
					SCTID_LOINC_CODE_SYSTEM,
					ltc.getConcept().getId());
		}
	}
	
	private Set<LoincTemplatedConcept> doModeling() throws TermServerScriptException {
		Set<LoincTemplatedConcept> successfullyModelledConcepts = new HashSet<>();
		for (Entry<String, Map<String, LoincDetail>> entry : loincDetailMap.entrySet()) {
			LoincTemplatedConcept templatedConcept = doModeling(entry.getKey(), entry.getValue());
			successfullyModelledConcepts.add(templatedConcept);
		}
		return successfullyModelledConcepts;
	}
	
	private LoincTemplatedConcept doModeling(String loincNum, Map<String, LoincDetail> loincDetailMap) throws TermServerScriptException {
		if (loincNum.equals("17849-1")) {
			debug("here");
		}
		
		if (!loincDetailMap.containsKey(LoincDetail.COMPONENT_PN) ||
				!loincDetailMap.containsKey(LoincDetail.COMPNUM_PN)) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					loincNumToLoincTermMap.get(loincNum).getDisplayName(),
					"Does not feature one of COMPONENT_PN or COMPNUM_PN");
			return null;
		}
		
		LoincTemplatedConcept templatedConcept = LoincTemplatedConcept.populateTemplate(loincNum, loincDetailMap);
		//populateCategorization(loincNum, templatedConcept.getConcept());
		if (templatedConcept == null) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					loincNumToLoincTermMap.get(loincNum).getDisplayName(),
					"Does not meet criteria for template match");
		} else {
			String fsn = templatedConcept.getConcept().getFsn();
			boolean insufficientTermPopulation = fsn.contains("[");
			if (insufficientTermPopulation) {
				templatedConcept.getConcept().addIssue("FSN indicates failure to populate required slot: " + fsn, ",\n");
			} else {
				doProposedModelComparison(loincNum, templatedConcept);
			}
			
			if (templatedConcept.getConcept().hasIssues()) {
				report(getTab(TAB_MODELING_ISSUES),
						loincNum,
						loincNumToLoincTermMap.get(loincNum).getDisplayName(),
						templatedConcept.getConcept().getIssues());
			}
		}
		flushFilesSoft();
		return templatedConcept;
	}

	/*private void populateCategorization(String loincNum, Concept concept) {
		//Do we have the full set of properties for this loincTerm?
		LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
		String order = loincTerm.getOrderObs();
		Concept categoryConcept = categorizationMap.get(order);
		RelationshipTemplate rt = new RelationshipTemplate(HasConceptCategorizationStatus, categoryConcept);
		concept.addRelationship(rt, SnomedUtils.getFirstFreeGroup(concept));
	}*/

	private void doProposedModelComparison(String loincNum, LoincTemplatedConcept loincTemplatedConcept) throws TermServerScriptException {
		//Do we have this loincNum
		Concept existingLoincConcept = loincNumToSnomedConceptMap.get(loincNum);
		Concept proposedLoincConcept = loincTemplatedConcept.getConcept();
		LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
		
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
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON), loincNum, existingLoincConceptStr, loincTemplatedConcept.getClass().getSimpleName(), proposedDescriptionsStr, existingSCG, proposedSCG, modelDiff, loincTerm.getCommonColumns());
	}

	private void populateLoincNumMap() throws TermServerScriptException {
		for (Concept c : LoincUtils.getActiveLOINCconcepts(gl)) {
			loincNumToSnomedConceptMap.put(LoincUtils.getLoincNumFromDescription(c), c);
		}
		info("Populated map of " + loincNumToSnomedConceptMap.size() + " LOINC concepts");
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
