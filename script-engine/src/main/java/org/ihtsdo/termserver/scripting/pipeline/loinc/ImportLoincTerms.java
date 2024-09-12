package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
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

	// Regular expression to find tokens within square brackets
	private static final String ALL_CAPS_SLOT_REGEX = "\\[([A-Z]+)\\]";
	private static final Pattern allCapsSlotPattern = Pattern.compile(ALL_CAPS_SLOT_REGEX);
	
	int existedPreviousIteration = 0;

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_LOINC_DETAIL_MAP_NOTES,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_IMPORT_STATUS,
			TAB_IOI,
			TAB_STATS};
	
	public static void main(String[] args) throws TermServerScriptException {
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
				"LoincNum, Item of Special Interest, LoincName, Issues, details",
				"LoincNum, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, "  + commonLoincColumns,
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count,Top Priority Usage, Higest Rank, HighestUsageCount",
				"Concept, FSN, SemTag, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Category, LoincNum, Detail, , , ",
				"Property, In Scope, Included, Included in Top 2K, Excluded, Excluded in Top 2K"
		};

		super.postInit(tabNames, columnHeadings, false);
		scheme = gl.getConcept(SCTID_LOINC_SCHEMA);
		externalContentModule = SCTID_LOINC_EXTENSION_MODULE;
		namespace = "1010000";
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		super.loadSupportingInformation();
		loadLoincDetail();
		loadPanels();
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		attributePartMapManager = new LoincAttributePartMapManager(this, partMap, partMapNotes);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
		LoincTemplatedConcept.initialise(this, loincDetailMap);
	}

	@Override
	protected Set<TemplatedConcept> doModeling() throws TermServerScriptException {
		Set<TemplatedConcept> successfullyModelledConcepts = new HashSet<>();
		for (Entry<String, Map<String, LoincDetail>> entry : loincDetailMap.entrySet()) {
			String loincNum = entry.getKey();
			LoincTemplatedConcept templatedConcept = doModeling(loincNum, entry.getValue());
			checkConceptSufficientlyModeled("Observable", loincNum, templatedConcept, successfullyModelledConcepts);
		}

		for (String panelLoincNum : panelLoincNums) {
			LoincTemplatedConcept templatedConcept = doPanelModeling(panelLoincNum);
			checkConceptSufficientlyModeled("Panel", panelLoincNum, templatedConcept, successfullyModelledConcepts);
		}

		return successfullyModelledConcepts;
	}

	private void checkConceptSufficientlyModeled(String contentType, String loincNum, LoincTemplatedConcept templatedConcept, Set<TemplatedConcept> successfullyModelledConcepts) throws TermServerScriptException {
		if (templatedConcept != null
				&& !templatedConcept.getConcept().hasIssue(FSN_FAILURE)
				&& !templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
			successfullyModelledConcepts.add(templatedConcept);
			incrementSummaryCount(ContentPipelineManager.CONTENT_COUNT, "Content added - " + contentType);
		} else {
			incrementSummaryCount(ContentPipelineManager.CONTENT_COUNT, "Content not added - " + contentType);
			if (!externalConceptMap.containsKey(loincNum)) {
				incrementSummaryCount("Missing LoincNums","LoincNum in Detail file not in LOINC.csv - " + loincNum);
			} else if (externalConceptMap.get(loincNum).isHighestUsage() && templatedConcept != null) {
				//Templates that come back as null will already have been counted as out of scope
				incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS,"Highest Usage Mapping Failure");
				report(getTab(TAB_IOI), "Highest Usage Mapping Failure", loincNum);
			}
		}
	}

	private LoincTemplatedConcept doModeling(String loincNum, Map<String, LoincDetail> loincDetailMap) throws TermServerScriptException {
		if (!confirmLoincNumExists(loincNum) || loincNumContainsObjectionableWord(loincNum)) {
			return null;
		}

		//Is this a loincnum that's being maintained manually?  Return what is already there if so.
		if (MANUALLY_MAINTAINED_ITEMS.containsKey(loincNum)) {
			LoincTemplatedConcept tc = LoincTemplatedConceptWithDefaultMap.create(loincNum);
			tc.setConcept(gl.getConcept(MANUALLY_MAINTAINED_ITEMS.get(loincNum)));
			return tc;
		}

		if (!loincDetailMap.containsKey(COMPONENT_PN) ||
				!loincDetailMap.containsKey(COMPNUM_PN)) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					ContentPipelineManager.getSpecialInterestIndicator(loincNum),
					getLoincTerm(loincNum).getDisplayName(),
					"Does not feature one of COMPONENT_PN or COMPNUM_PN");
			return null;
		}

		LoincTemplatedConcept templatedConcept = LoincTemplatedConcept.populateTemplate(loincNum, loincDetailMap);
		validateTemplatedConcept(loincNum, templatedConcept);
		return templatedConcept;
	}

	private void validateTemplatedConcept(String loincNum, LoincTemplatedConcept templatedConcept) throws TermServerScriptException {
		if (templatedConcept == null) {
			LoincTerm loincTerm = getLoincTerm(loincNum);
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					ContentPipelineManager.getSpecialInterestIndicator(loincNum),
					loincTerm.getDisplayName(),
					"Does not meet criteria for template match",
					"Property: " + loincTerm.getProperty());
		} else {
			String fsn = templatedConcept.getConcept().getFsn();
			boolean insufficientTermPopulation = fsn.contains("[");
			//Some panels have words like '[Moles/volume]' in them, so check also for slot token names (all caps).  Not Great.
			if (insufficientTermPopulation && hasAllCapsSlot(fsn)) {
				templatedConcept.getConcept().addIssue(FSN_FAILURE + " to populate required slot: " + fsn);
			}

			if (templatedConcept.getConcept().hasIssues() ) {
				report(getTab(TAB_MODELING_ISSUES),
						loincNum,
						ContentPipelineManager.getSpecialInterestIndicator(loincNum),
						getLoincTerm(loincNum).getDisplayName(),
						templatedConcept.getConcept().getIssues(",\n"));
			}
		}
		flushFilesSoft();
	}

	/**
	 * Checks if a string contains tokens enclosed in square brackets that are all in capital letters.
	 *
	 * @param fsn The string to check.
	 * @return true if there is at least one token in all caps within square brackets; false otherwise.
	 */
	private boolean hasAllCapsSlot(String fsn) {
		Matcher matcher = allCapsSlotPattern.matcher(fsn);
		return matcher.find();
	}

	private boolean loincNumContainsObjectionableWord(String loincNum) throws TermServerScriptException {
		//Does this LoincNum feature an objectionable word?  Skip if so.
		for (String objectionableWord : objectionableWords) {
			LoincTerm loincTerm = getLoincTerm(loincNum);
			if (loincTerm.getDisplayName() == null) {
				LOGGER.debug("Unable to obtain display name for {}", loincNum);
			} else if (loincTerm.getDisplayName().toLowerCase().contains(" " + objectionableWord + " ")) {
				report(getTab(TAB_MODELING_ISSUES),
						loincNum,
						ContentPipelineManager.getSpecialInterestIndicator(loincNum),
						loincTerm.getDisplayName(),
						"Contains objectionable word - " + objectionableWord);
				return true;
			}
		}
		return false;
	}

	private boolean confirmLoincNumExists(String loincNum) throws TermServerScriptException {
		//Do we have consistency between the detail map and the main loincTermMap?
		if (!externalConceptMap.containsKey(loincNum)) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					ContentPipelineManager.getSpecialInterestIndicator(loincNum),
					"N/A",
					"Failed integrity. Loinc Term " + loincNum + " from detail file, not known in LOINC.csv");
			return false;
		}
		return true;
	}

	private LoincTemplatedConcept doPanelModeling(String panelLoincNum) throws TermServerScriptException {
		//Don't do objectionable word check on panels - 'panel' is our only current objectionable word!
		if (!confirmLoincNumExists(panelLoincNum)) {
			return null;
		}

		LoincTemplatedConcept templatedConcept = LoincTemplatedConceptPanel.create(panelLoincNum);
		validateTemplatedConcept(panelLoincNum, templatedConcept);
		return templatedConcept;
	}

	protected void doProposedModelComparison(TemplatedConcept loincTemplatedConcept) throws TermServerScriptException {
		//Do we have this loincNum
		Concept proposedLoincConcept = loincTemplatedConcept.getConcept();
		Concept existingConcept = loincTemplatedConcept.getExistingConcept();
		String loincNum = loincTemplatedConcept.getExternalIdentifier();
		LoincTerm loincTerm = getLoincTerm(loincNum);
		
		String previousSCG = existingConcept == null ? "N/A" : existingConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedSCG = proposedLoincConcept == null ? "N/A" : proposedLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedDescriptionsStr = proposedLoincConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(proposedLoincConcept);
		//We might have inactivated descriptions in the existing concept if they've been changed, so
		String previousDescriptionsStr = existingConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(existingConcept, true);
		String existingConceptId = existingConcept == null ? "N/A" : existingConcept.getId();
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON),
				loincNum, 
				proposedLoincConcept != null ? proposedLoincConcept.getId() : existingConceptId,
				loincTemplatedConcept.getIterationIndicator(),
				loincTemplatedConcept.getClass().getSimpleName(),
				loincTemplatedConcept.getDifferencesFromExistingConcept(),
				proposedDescriptionsStr,
				previousDescriptionsStr,
				proposedSCG, 
				previousSCG,
				loincTerm.getCommonColumns());
	}

	
}
