package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * LE-3
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportLoincTerms extends LoincScript implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImportLoincTerms.class);

	protected static Set<String> objectionableWords = new HashSet<>(Arrays.asList("panel"));

	protected static final String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
	private static final String commonLoincColumns = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f1 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2023.tsv"  
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f3 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	//-f5 "G:\My Drive\018_Loinc\2023\Loinc_Detail_Type_1_2.75_Active_Lab_NonVet.tsv"
	
	public static final String FSN_FAILURE = "FSN indicates failure";
	
	//private List<String> previousIterationLoincNums = new ArrayList<>();
	int existedPreviousIteration = 0;

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_LOINC_DETAIL_MAP_NOTES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_IMPORT_STATUS,
			TAB_IOI };
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		new ImportLoincTerms().ingestExternalContent(args);
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
				"LoincNum, This Iteration, Template, Proposed Descriptions, Current Model, Proposed Model, Difference,"  + commonLoincColumns,
				"PartNum, PartName, PartType, PriorityIndex, Usage Count, Top Priority Usage, ",
				"Concept, FSN, SemTag, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Category, LoincNum, Detail, , , "
		};

		super.postInit(tabNames, columnHeadings, false);
		scheme = gl.getConcept(SCTID_LOINC_SCHEMA);
		externalContentModule = SCTID_LOINC_EXTENSION_MODULE;
	}

	/*private void runReport() throws TermServerScriptException, InterruptedException {
		//determineExistingConcepts(getTab(TAB_TOP_100));
		importIntoTask(successfullyModelled);
		generateAlternateIdentifierFile(successfullyModelled);
	}*/


	@Override
	protected void importExternalContent() throws TermServerScriptException {
		//ExecutorService executor = Executors.newCachedThreadPool();
		AttributePartMapManager.validatePartAttributeMap(gl, getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
		//populateLoincNumMap();
		loadLoincParts();
		//We can look at the full LOINC file in parallel as it's not needed for modelling
		//executor.execute(() -> loadFullLoincFile());
		loadFullLoincFile(NOT_SET);
		loadLoincDetail();

	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		attributePartMapManager = new AttributePartMapManager(this, loincParts, partMapNotes);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));

		//reportOnDetailMappingWithUsage();

		//loadLastIterationLoincNums();
		LoincTemplatedConcept.initialise(this, gl, attributePartMapManager, loincNumToLoincTermMap, loincDetailMap, loincParts);
	}

/*	private void loadLastIterationLoincNums() {
		LOGGER.info ("Loading LoincNums from previous iteration: " + getInputFile(FILE_IDX_PREVIOUS_ITERATION));
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(getInputFile(FILE_IDX_PREVIOUS_ITERATION)));
			String line = in.readLine();
			while (line != null) {
				previousIterationLoincNums.add(line.trim());
				line = in.readLine();
			}
			LOGGER.info("Loaded " + previousIterationLoincNums.size() + " previous iterations loincNums");
		} catch (Exception e) {
			throw new RuntimeException("Failed to load " + getInputFile(FILE_IDX_PREVIOUS_ITERATION), e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {}
			}
		}
	}*/

	/*private void reportOnDetailMappingWithUsage() throws TermServerScriptException {
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
	}*/

	/*private void populateLoincPartNumUsage(String loincNum, List<String> partNumTypesOfInterest, Map<String, LoincUsage> loincPartNumUsages, Map<String, Set<String>> LDTColumnNamesForPart) {
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
	}*/

	@Override
	protected Set<TemplatedConcept> doModeling() throws TermServerScriptException {
		Set<TemplatedConcept> successfullyModelledConcepts = new HashSet<>();
		for (Entry<String, Map<String, LoincDetail>> entry : loincDetailMap.entrySet()) {
			LoincTemplatedConcept templatedConcept = doModeling(entry.getKey(), entry.getValue());
			if (templatedConcept != null
				&& !templatedConcept.getConcept().hasIssue(FSN_FAILURE)
				&& !templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
				successfullyModelledConcepts.add(templatedConcept);
			}
		}
		/*Set<String> successfullyModelledLoincNums = successfullyModelledConcepts.stream()
				.map(ltc -> ltc.getExternalIdentifier())
				.collect(Collectors.toSet());
		
		Report LoincNums modelled in the previous iteration that didn't make this round
		int removedThisIteration = 0;
		for (String loincNum : previousIterationLoincNums) {
			if (!successfullyModelledLoincNums.contains(loincNum)) {
				removedThisIteration++;
				report(getTab(TAB_PROPOSED_MODEL_COMPARISON),
						loincNum,
						"REMOVED",
						"",
						loincNumToLoincTermMap.get(loincNum).getDisplayName());
			}
		}*/

		//Report summary of LoincNums existing/new/removed
		int newThisIteration = successfullyModelledConcepts.size() - existedPreviousIteration;
		int tabIdx = getTab(TAB_SUMMARY);
		report(tabIdx, "");
		report(tabIdx, "LoincNums existing in previous iteration", existedPreviousIteration);
		report(tabIdx, "LoincNums new this iteration", newThisIteration);
		//report(tabIdx, "LoincNums removed this iteration", removedThisIteration);

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

		//Does this LoincNum feature an objectionable word?  Skip if so.
		for (String objectionableWord : objectionableWords) {
			if (loincNumToLoincTermMap.get(loincNum).getDisplayName() == null) {
				LOGGER.debug("Unable to obtain display name for " + loincNum);
			}
			if (loincNumToLoincTermMap.get(loincNum).getDisplayName().toLowerCase().contains(" " + objectionableWord + " ")) {
				report(getTab(TAB_MODELING_ISSUES),
						loincNum,
						loincNumToLoincTermMap.get(loincNum).getDisplayName(),
						"Contains objectionable word - " + objectionableWord);
				return null;
			}
		}
		
		LoincTemplatedConcept templatedConcept = LoincTemplatedConcept.populateTemplate(this, loincNum, loincDetailMap);
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
			} else if (!templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
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

	private void doProposedModelComparison(String loincNum, LoincTemplatedConcept loincTemplatedConcept) throws TermServerScriptException {
		//Do we have this loincNum
		Concept proposedLoincConcept = loincTemplatedConcept.getConcept();
		LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
		
		String existingSCG = "N/A";
		String modelDiff = "";
		String proposedSCG = proposedLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		
		/*String existingLoincConceptStr = "N/A";
		Did we have this loincNum in the previous iteration?
		String previousIterationIndicator = "NEW";
		if (previousIterationLoincNums.contains(loincNum)) {
			previousIterationIndicator = "EXISTING";
			existedPreviousIteration++;
		}*/
		
		String proposedDescriptionsStr = SnomedUtils.prioritise(proposedLoincConcept.getDescriptions()).stream()
				.map(d -> d.toString())
				.collect(Collectors.joining("\n"));
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON), loincNum, /*previousIterationIndicator*/ "N/A", loincTemplatedConcept.getClass().getSimpleName(), proposedDescriptionsStr, existingSCG, proposedSCG, modelDiff, loincTerm.getCommonColumns());
	}

	/*private void populateLoincNumMap() throws TermServerScriptException {
		for (Concept c : LoincUtils.getActiveLOINCconcepts(gl)) {
			loincNumToSnomedConceptMap.put(LoincUtils.getLoincNumFromDescription(c), c);
		}
		LOGGER.info("Populated map of " + loincNumToSnomedConceptMap.size() + " LOINC concepts");
	}*/
	
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
