package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	// Regular expression to find tokens within square brackets
	private static final String allCapsSlotRegex = "\\[([A-Z]+)\\]";
	private static final Pattern allCapsSlotPattern = Pattern.compile(allCapsSlotRegex);
	
	//private List<String> previousIterationLoincNums = new ArrayList<>();
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
				"LoincNum, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, "  + commonLoincColumns,
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count, Top Priority Usage, Higest Rank",
				"Concept, FSN, SemTag, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Category, LoincNum, Detail, , , ",
				"Property, Included, Excluded, Excluded in Top 20K"
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
		attributePartMapManager = new AttributePartMapManager(this, loincParts, partMapNotes);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
		LoincTemplatedConcept.initialise(this, gl, attributePartMapManager, loincNumToLoincTermMap, loincDetailMap, loincParts);
	}

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

		for (String panelLoincNum : panelLoincNums) {
			LoincTemplatedConcept templatedConcept = doPanelModeling(panelLoincNum);
			if (templatedConcept != null
					&& !templatedConcept.getConcept().hasIssue(FSN_FAILURE)
					&& !templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
				successfullyModelledConcepts.add(templatedConcept);
				summaryCounts.merge("Panel successfully added", 1, Integer::sum);
			} else {
				summaryCounts.merge("Panel unsuccessfully added", 1, Integer::sum);
			}
		}

		return successfullyModelledConcepts;
	}
	
	private LoincTemplatedConcept doModeling(String loincNum, Map<String, LoincDetail> loincDetailMap) throws TermServerScriptException {
		if (!confirmLoincNumExists(loincNum) || loincNumContainsObjectionableWord(loincNum)) {
			return null;
		}

		if (!loincDetailMap.containsKey(COMPONENT_PN) ||
				!loincDetailMap.containsKey(COMPNUM_PN)) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					loincNumToLoincTermMap.get(loincNum).getDisplayName(),
					"Does not feature one of COMPONENT_PN or COMPNUM_PN");
			return null;
		}

		LoincTemplatedConcept templatedConcept = LoincTemplatedConcept.populateTemplate(this, loincNum, loincDetailMap);
		validateTemplatedConcept(loincNum, templatedConcept);
		return templatedConcept;
	}

	private void validateTemplatedConcept(String loincNum, LoincTemplatedConcept templatedConcept) throws TermServerScriptException {
		if (templatedConcept == null) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					loincNumToLoincTermMap.get(loincNum).getDisplayName(),
					"Does not meet criteria for template match");
		} else {
			String fsn = templatedConcept.getConcept().getFsn();
			boolean insufficientTermPopulation = fsn.contains("[");
			//Some panels have words like '[Moles/volume]' in them, so check also for slot token names (all caps).  Not Great.
			if (insufficientTermPopulation && isAllCapsSlot(fsn)) {
				templatedConcept.getConcept().addIssue(FSN_FAILURE + " to populate required slot: " + fsn, ",\n");
			} else if (!templatedConcept.hasProcessingFlag(ProcessingFlag.DROP_OUT)) {
				//We'll move this call to later when we work out the change set
				//doProposedModelComparison(loincNum, templatedConcept);
			}

			if (templatedConcept.getConcept().hasIssues() ) {
				report(getTab(TAB_MODELING_ISSUES),
						loincNum,
						loincNumToLoincTermMap.get(loincNum).getDisplayName(),
						templatedConcept.getConcept().getIssues());
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
	private boolean isAllCapsSlot(String fsn) {
		Matcher matcher = allCapsSlotPattern.matcher(fsn);

		while (matcher.find()) {
			// If a match is found, return true
			return true;
		}
		// If no all-caps tokens are found within square brackets, return false
		return false;
	}

	private boolean loincNumContainsObjectionableWord(String loincNum) throws TermServerScriptException {
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
				return true;
			}
		}
		return false;
	}

	private boolean confirmLoincNumExists(String loincNum) throws TermServerScriptException {
		//Do we have consistency between the detail map and the main loincTermMap?
		if (!loincNumToLoincTermMap.containsKey(loincNum)) {
			report(getTab(TAB_MODELING_ISSUES),
					loincNum,
					"N/A",
					"Failed integrity. Loinc Term " + loincNum + " from detail file, not known in LOINC.csv");
			return false;
		}
		return true;
	}

	private LoincTemplatedConcept doPanelModeling(String panelLoincNum) throws TermServerScriptException {
		if (!confirmLoincNumExists(panelLoincNum) || loincNumContainsObjectionableWord(panelLoincNum)) {
			return null;
		}

		LoincTemplatedConcept templatedConcept = LoincTemplatedConceptPanel.create(panelLoincNum);
		validateTemplatedConcept(panelLoincNum, templatedConcept);
		return templatedConcept;
	}

	protected void doProposedModelComparison(String loincNum, TemplatedConcept loincTemplatedConcept, Concept existingConcept, String previousIterationIndicator, String differencesStr) throws TermServerScriptException {
		//Do we have this loincNum
		Concept proposedLoincConcept = null;
		if (loincTemplatedConcept != null) {
			proposedLoincConcept = loincTemplatedConcept.getConcept();
		}
		LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
		
		String previousSCG = existingConcept == null ? "N/A" : existingConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedSCG = proposedLoincConcept == null ? "N/A" : proposedLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String proposedDescriptionsStr = proposedLoincConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(proposedLoincConcept);
		//We might have inactivated descriptions in the existing concept if they've been changed, so
		String previousDescriptionsStr = existingConcept == null ? "N/A" : SnomedUtils.getDescriptionsToString(existingConcept, true);
		String existingConceptId = existingConcept == null ? "N/A" : existingConcept.getId();
		report(getTab(TAB_PROPOSED_MODEL_COMPARISON),
				loincNum, 
				proposedLoincConcept != null ? proposedLoincConcept.getId() : existingConceptId,
				previousIterationIndicator,
				loincTemplatedConcept == null ? "N/A" : loincTemplatedConcept.getClass().getSimpleName(),
				differencesStr,
				proposedDescriptionsStr,
				previousDescriptionsStr,
				proposedSCG, 
				previousSCG,
				loincTerm.getCommonColumns());
	}

	
}
