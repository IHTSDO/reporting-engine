package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * LE-3
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class    ImportLoincTerms extends LoincScript implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImportLoincTerms.class);

	protected static final String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	private static final String commonLoincColumns = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f1 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2023.tsv"  
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f3 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	//-f5 "G:\My Drive\018_Loinc\2023\Loinc_Detail_Type_1_2.75_Active_Lab_NonVet.tsv"
	
	public static final String FSN_FAILURE = "FSN indicates failure";
	
	Rf2ConceptCreator conceptCreator;
	
	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_LOINC_DETAIL_MAP_NOTES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_IMPORT_STATUS,
			TAB_IOI };
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ImportLoincTerms report = new ImportLoincTerms();
		try {
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.init(args);
			report.loadProjectSnapshot(false);
			report.postInit();
			report.conceptCreator = Rf2ConceptCreator.build(report, report.getInputFile(FILE_IDX_CONCEPT_IDS), report.getInputFile(FILE_IDX_DESC_IDS), null);
			report.conceptCreator.initialiseGenerators(new String[]{"-nS","1010000", "-iR", "16470", "-m", "11010000107"});
			report.runReport();
		} finally {
			while (report.additionalThreadCount > 0) {
				Thread.sleep(1000);
			}
			report.finish();
			if (report.conceptCreator != null) {
				report.conceptCreator.finish();
			}
		}
	}

	protected String[] getTabNames() {
		return tabNames;
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				/*"LoincNum, LongCommonName, Concept, Correlation, Expression," + commonLoincColumns,*/
				/*"LoincNum, LoincPartNum, Advice, LoincPartName, SNOMED Attribute, ", */
				"Item, Info, Details, ,",
				"LoincPartNum, LoincPartName, PartType, ColumnName, Part Status, SCTID, FSN, Priority Index, Usage Count, Top Priority Usage, Mapping Notes,",
				"LoincNum, LoincName, Issues, ",
				"LoincNum, Existing Concept, Template, Proposed Descriptions, Current Model, Proposed Model, Difference,"  + commonLoincColumns,
				"PartNum, PartName, PartType, PriorityIndex, Usage Count, Top Priority Usage, ",
				"Concept, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Category, LoincNum, Detail, , , "
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
		AttributePartMapManager.validatePartAttributeMap(gl, getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
		populateLoincNumMap();
		loadLoincParts();
		//We can look at the full LOINC file in parallel as it's not needed for modelling
		//executor.execute(() -> loadFullLoincFile());
		loadFullLoincFile(NOT_SET);
		loadLoincDetail();
		attributePartMapManager = new AttributePartMapManager(this, loincParts, partMapNotes);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));

		reportOnDetailMappingWithUsage();

		LoincTemplatedConcept.initialise(this, gl, attributePartMapManager, loincNumToLoincTermMap, loincDetailMap, loincParts);
		//determineExistingConcepts(getTab(TAB_TOP_100));
		Set<LoincTemplatedConcept> successfullyModelled = doModeling();
		LoincTemplatedConcept.reportStats(getTab(TAB_SUMMARY));
		//LoincTemplatedConcept.reportFailedMappingsByProperty(getTab(TAB_MODELING_ISSUES));
		LoincTemplatedConcept.reportMissingMappings(getTab(TAB_MAP_ME));
		flushFiles(false);
		for (LoincTemplatedConcept tc : successfullyModelled) {
			Concept concept = tc.getConcept();
			try {
				conceptCreator.copyStatedRelsToInferred(concept);
				conceptCreator.writeConceptToRF2(getTab(TAB_IMPORT_STATUS), concept, tc.getLoincNum(), SCTID_LOINC_EXTENSION_MODULE);
			} catch (Exception e) {
				report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getLoincNum(), e);
			}
		}
		conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
		
		/*importIntoTask(successfullyModelled);
		generateAlternateIdentifierFile(successfullyModelled);*/
	}

	private void reportOnDetailMappingWithUsage() throws TermServerScriptException {
		Map<String, LoincUsage> loincPartNumUsages = new HashMap<>();
		Map<String, Set<String>> LDTColumnNamesForPart = new HashMap<>();
		List<String> partNumTypesOfInterest = Arrays.asList("COMPONENT", "CHALLENGE", "DIVISORS", "METHOD", "PROPERTY", "SCALE", "SYSTEM", "TIME");
		// Work through the loincDetailMap for each loincNum to generate usage
		// (and calculate a priority index) for each loincPart
		for (String loincNum: loincDetailMap.keySet()) {
			populateLoincPartNumUsage(loincNum, partNumTypesOfInterest, loincPartNumUsages, LDTColumnNamesForPart);
		}

		int partDetailItemsMapped = 0;
		int partDetailItemsNotMapped = 0;
		int partDetailWithUnlistedPart = 0;
		int partDetailItemsWithNotes = 0;
		//Now output each of those parts, indicating if there is a mapping available
		for (Map.Entry<String, LoincUsage> entry : loincPartNumUsages.entrySet()) {
			String loincPartNum = entry.getKey();
			LoincUsage usage = entry.getValue();
			LoincPart loincPart = loincParts.get(loincPartNum);
			if (loincPart == null) {
				partDetailWithUnlistedPart++;
			}
			String partName = loincPart == null ? "Unlisted" : loincPart.getPartName();
			String partTypeName = loincPart == null ? "Unlisted" : loincPart.getPartTypeName();
			String partStatus = loincPart == null ? "Unlisted" : loincPart.getStatus().name();
			RelationshipTemplate rt = attributePartMapManager.getPartMappedAttributeForType(NOT_SET, null, loincPartNum, null);
			String targetSctId = rt == null? "" : rt.getTarget().getId();
			String targetFSN = rt == null? "" : rt.getTarget().getFsn();
			if (rt == null) {
				partDetailItemsNotMapped++;
			} else {
				partDetailItemsMapped++;
			}

			String notes = getPartMapNotes(loincPartNum);
			if (!StringUtils.isEmpty(notes)) {
				partDetailItemsWithNotes++;
			}

			report(getTab(TAB_LOINC_DETAIL_MAP_NOTES),
					loincPartNum, partName,
					partTypeName,
					LDTColumnNamesForPart.get(loincPartNum).stream().collect(Collectors.joining(", \n")),
					partStatus,
					targetSctId, targetFSN,
					usage.getPriority(),
					usage.getCount(),
					usage.getTopRankedLoincTermsStr(),
					notes);
		}

		int summaryTabIdx = getTab(TAB_SUMMARY);
		report(summaryTabIdx, "");
		report(summaryTabIdx, "Part Detail Items Mapped",partDetailItemsMapped);
		report(summaryTabIdx, "Part Detail Items Not Mapped",partDetailItemsNotMapped);
		report(summaryTabIdx, "Part Detail Items With Notes",partDetailItemsWithNotes);
		report(summaryTabIdx, "Part Detail Items With Unlisted Part",partDetailWithUnlistedPart);

		//Are there any mapped parts that we've provided that are not used?
		for (String mappedLoincPartNum : attributePartMapManager.getAllMappedLoincPartNums()) {
			if (!loincPartNumUsages.containsKey(mappedLoincPartNum)) {
				report(summaryTabIdx, mappedLoincPartNum, " is mapped but no longer used in detail file");
			}
		}
	}

	private void populateLoincPartNumUsage(String loincNum, List<String> partNumTypesOfInterest, Map<String, LoincUsage> loincPartNumUsages, Map<String, Set<String>> LDTColumnNamesForPart) {
		for (LoincDetail loincDetail : loincDetailMap.get(loincNum).values()) {
			// Is this a part we're interested in?
			if (!partNumTypesOfInterest.contains(loincDetail.getPartTypeName())) {
				continue;
			}
			String loincPartNum = loincDetail.getPartNumber();
			LoincUsage usage = loincPartNumUsages.get(loincPartNum);
			if (usage == null) {
				usage = new LoincUsage();
				loincPartNumUsages.put(loincPartNum, usage);
			}
			usage.add(loincNumToLoincTermMap.get(loincNum));

			// Have we seen this part before?  Record the LDTColumnNames.
			Set<String> LDTColumnNames = LDTColumnNamesForPart.get(loincPartNum);
			if (LDTColumnNames == null) {
				LDTColumnNames = new HashSet<>();
				LDTColumnNamesForPart.put(loincPartNum, LDTColumnNames);
			}
			LDTColumnNames.add(loincDetail.getLDTColumnName());
		}
	}
	
	/*private void generateAlternateIdentifierFile(Set<LoincTemplatedConcept> ltcs) throws TermServerScriptException {
		for (LoincTemplatedConcept ltc : ltcs) {
			report(getTab(TAB_RF2_IDENTIFIER_FILE),
					ltc.getLoincNum(),
					today,
					"1",
					SCTID_LOINC_EXTENSION_MODULE,
					SCTID_LOINC_CODE_SYSTEM,
					ltc.getConcept().getId());
		}
	}*/
	
	private Set<LoincTemplatedConcept> doModeling() throws TermServerScriptException {
		Set<LoincTemplatedConcept> successfullyModelledConcepts = new HashSet<>();
		for (Entry<String, Map<String, LoincDetail>> entry : loincDetailMap.entrySet()) {
			LoincTemplatedConcept templatedConcept = doModeling(entry.getKey(), entry.getValue());
			if (templatedConcept != null && !templatedConcept.getConcept().hasIssue(FSN_FAILURE)) {
				successfullyModelledConcepts.add(templatedConcept);
			}
		}
		return successfullyModelledConcepts;
	}
	
	private LoincTemplatedConcept doModeling(String loincNum, Map<String, LoincDetail> loincDetailMap) throws TermServerScriptException {
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
				templatedConcept.getConcept().addIssue(FSN_FAILURE + " to populate required slot: " + fsn, ",\n");
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
				.collect(Collectors.joining("\n"));
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON), loincNum, existingLoincConceptStr, loincTemplatedConcept.getClass().getSimpleName(), proposedDescriptionsStr, existingSCG, proposedSCG, modelDiff, loincTerm.getCommonColumns());
	}

	private void populateLoincNumMap() throws TermServerScriptException {
		for (Concept c : LoincUtils.getActiveLOINCconcepts(gl)) {
			loincNumToSnomedConceptMap.put(LoincUtils.getLoincNumFromDescription(c), c);
		}
		LOGGER.info("Populated map of " + loincNumToSnomedConceptMap.size() + " LOINC concepts");
	}
	
	/*private void importIntoTask(Set<LoincTemplatedConcept> successfullyModelled) throws TermServerScriptException {
		//TODO Move this class to be a BatchFix so we don't need a plug in class
		CreateConceptsPreModelled batchProcess = new CreateConceptsPreModelled(getReportManager(), getTab(TAB_IMPORT_STATUS), successfullyModelled);
		batchProcess.setProject(new Project("LE","MAIN/SNOMEDCT-LOINCEXT/LE"));
		BatchFix.headlessEnvironment = NOT_SET;  //Avoid asking any more questions to the user
		batchProcess.setServerUrl(getServerUrl());
		String[] args = new String[] { "-a", "pwilliams", "-n", "500", "-p", "LE", "-d", "N", "-c", authenticatedCookie };
		batchProcess.inflightInit(args);
		batchProcess.runJob();
	}*/
}
