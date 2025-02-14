package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.pipeline.*;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincTerm;
import org.ihtsdo.termserver.scripting.pipeline.loinc.template.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ImportLoincTerms extends LoincScript implements LoincScriptConstants {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImportLoincTerms.class);

	private static final String COMMON_LOINC_COLUMNS = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f1 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2023.tsv"  
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f3 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	//-f5 "G:\My Drive\018_Loinc\2023\Loinc_Detail_Type_1_2.75_Active_Lab_NonVet.tsv"

	private final Set<UUID> existingEnGbLangRefsetIds = new HashSet<>();
	
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

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return switch (externalConcept.getProperty()) {
			case "ArVRat", "CRat", "MRat", "SRat", "VRat" -> LoincTemplatedConceptWithProcess.create(externalConcept);
			case "RelTime", "Time", "Vel", "RelVel" -> LoincTemplatedConceptWithProcessNoOutput.create(externalConcept);
			case "NFr", "MFr", "CFr", "AFr",  "SFr", "VFr" -> LoincTemplatedConceptWithRelative.create(externalConcept);
			case "ACnc", "Angle", "CCnc", "CCnt", "Diam", "EntCat", "EntLen", "EntMass", "EntNum", "EntSub",
				 "LaCnc", "Len", "LnCnc", "LsCnc", "Mass", "MCnc", "MCnt", "MoM", "Naric", "NCnc",
				 "PPres", "Pres", "PrThr", "SCnc", "SCnt", "Sub", "Titr" ->
					LoincTemplatedConceptWithComponent.create(externalConcept);
			case "MRto", "Ratio", "SRto" -> LoincTemplatedConceptWithRatio.create(externalConcept);
			case "Anat", "Aper", "Color", "Disposition", "DistWidth", "EntMCnc", "EntMeanVol", "EntVol",
			     "ID", "Morph", "Osmol", "Prid", "Rden", "Source", "SpGrav", "Temp", "Type", "Visc", "Vol" ->
					createTemplateBasedOnProperties(externalConcept);
			case "Susc" -> LoincTemplatedConceptWithSusceptibility.create(externalConcept);
			default -> TemplatedConceptNull.create(externalConcept);
		};
	}

	private TemplatedConcept createTemplateBasedOnProperties(ExternalConcept externalConcept) throws TermServerScriptException {
		if (externalConcept.getExternalIdentifier().equals(ContentPipelineManager.DUMMY_EXTERNAL_IDENTIFIER)) {
			//Pick one just to say that this type is "in scope".
			return LoincTemplatedConceptWithInheres.create(externalConcept);
		}

		//We would normally check a detail from within the templated concept itself
		Map<String, LoincDetail> loincDetailMap = loincDetailMapOfMaps.get(externalConcept.getExternalIdentifier());
		if (loincDetailMap != null) {
			if (loincDetailMap.containsKey("COMPNUM_PN")) {
				String partNum = loincDetailMap.get("COMPNUM_PN").getPartNumber();
				if (partNum.equals(LOINC_OBSERVATION_PART)) {
					return LoincTemplatedConceptWithInheresNoComponent.create(externalConcept);
				} else {
					return LoincTemplatedConceptWithInheres.create(externalConcept);
				}
			} else {
				throw new TermServerScriptException("No Component part found for " + externalConcept.getExternalIdentifier());
			}
		}
		throw new TermServerScriptException("No detail map found for " + externalConcept.getExternalIdentifier());
	}

	@Override
	protected String[] getTabNames() {
		return tabNames;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				/*"LoincNum, LongCommonName, Concept, Correlation, Expression," + commonLoincColumns,*/
				/*"LoincNum, LoincPartNum, Advice, LoincPartName, SNOMED Attribute, ", */
				"Item, Info, Details, ,",
				"LoincPartNum, LoincPartName, PartType, ColumnName, Part Status, SCTID, FSN, Priority Index, Usage Count, Top Priority Usage, Mapping Notes,",
				"LoincNum, Item of Special Interest, LoincName, Issues, details",
				"LoincNum, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, "  + COMMON_LOINC_COLUMNS,
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count,Top Priority Usage, Higest Rank, HighestUsageCount",
				"Concept, FSN, SemTag, Severity, Action, LoincNum, Descriptions, Expression, Status, , ",
				"Category, LoincNum, Detail, , , ",
				"Property, In Scope, Included, Included in Top 2K, Excluded, Excluded in Top 2K"
		};

		postInit(tabNames, columnHeadings);
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
		LoincTemplatedConcept.initialise(this, loincDetailMapOfMaps);
	}

	@Override
	protected void doModeling() throws TermServerScriptException {
		for (String loincNum : loincDetailMapOfMaps.keySet()) {
			TemplatedConcept templatedConcept = modelExternalConcept(loincNum);
			validateTemplatedConcept(loincNum, templatedConcept);
			if (conceptSufficientlyModeled("Observable", loincNum, templatedConcept)) {
				successfullyModelled.add(templatedConcept);
			}
		}

		for (String panelLoincNum : panelLoincNums) {
			LoincTemplatedConcept templatedConcept = doPanelModeling(panelLoincNum);
			validateTemplatedConcept(panelLoincNum, templatedConcept);
			if (conceptSufficientlyModeled("Panel", panelLoincNum, templatedConcept)) {
				successfullyModelled.add(templatedConcept);
			}
		}
	}

	private LoincTemplatedConcept doPanelModeling(String panelLoincNum) throws TermServerScriptException {
		//Don't do objectionable word check on panels - 'panel' is our only current objectionable word!
		if (!confirmExternalIdentifierExists(panelLoincNum)) {
			return null;
		}

		ExternalConcept panelTerm = getLoincTerm(panelLoincNum);
		return LoincTemplatedConceptPanel.create(panelTerm);
	}

	@Override
	protected void determineChangeSet() throws TermServerScriptException {

		//We've decided not to do an en-gb language refset for now, so existing content we'll set to inactive
		//and newly created en-gb we'll just delete and not put forward
		for (TemplatedConcept tc : successfullyModelled) {
			removeEnGbLangRefsets(tc.getConcept());
			removeEnGbLangRefsets(tc.getExistingConcept());
		}
		super.determineChangeSet();
	}

	private void removeEnGbLangRefsets(Concept c) {
		if (c == null) {
			return;
		}

		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry lre : d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET)) {
				if (existingEnGbLangRefsetIds.contains(UUID.fromString(lre.getId()))) {
					lre.setActive(false);
					incrementSummaryCount(ContentPipelineManager.LANG_REFSET_REMOVAL, "En-gb lang refset inactivated");
				} else {
					d.getLangRefsetEntries().remove(lre);
					incrementSummaryCount(ContentPipelineManager.LANG_REFSET_REMOVAL, "En-gb lang refset deleted");
				}
			}
		}
	}

	@Override
	protected void preModelling() throws TermServerScriptException {
		//Temporarily we're going to cache a list of LOINC en-gb langrefset UUIDs
		//So that - before we examine the change set, we can inactivate or remove them
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		LOGGER.info("Noting existing en-gb lang refsets");
		for (String existingSCTID : altIdentifierMap.values()) {
			Concept c = gl.getConcept(existingSCTID);
			for (Description d : c.getDescriptions()) {
				for (LangRefsetEntry lre : d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET)) {
					existingEnGbLangRefsetIds.add(UUID.fromString(lre.getId()));
				}
			}
		}
		LOGGER.info("Stored {} existing en-gb lang refsets", existingEnGbLangRefsetIds.size());
	}

	@Override
	protected void postModelling() throws TermServerScriptException {
		//We will need to import the existing OBS/ORD Refsets and work out what needs to change, but
		//for the moment we have the luxury of a greenfield site.  Just create them.
		for (TemplatedConcept tc : successfullyModelled) {
			if (tc instanceof LoincTemplatedConcept ltc) {
				LoincTerm loincTerm = ltc.getLoincTerm();
				switch (loincTerm.getOrderObs()) {
					case "Order" -> createNewRefsetMember(ltc, ORD_REFSET);
					case "Observation" -> createNewRefsetMember(ltc, OBS_REFSET);
					case "Both" -> {
						createNewRefsetMember(ltc, ORD_REFSET);
						createNewRefsetMember(ltc, OBS_REFSET);
					}
					default -> {
						//Do nothing
					}
				}
			}
		}
	}

	private void createNewRefsetMember(LoincTemplatedConcept ltc, Concept refset) throws TermServerScriptException {
		RefsetMember rm = new RefsetMember();
		rm.setModuleId(externalContentModule);
		rm.setReferencedComponentId(ltc.getConcept().getId());
		rm.setActive(true, true);
		rm.setRefsetId(refset.getId());
		rm.setId(UUID.randomUUID().toString());
		rm.setDirty();
		incrementSummaryCount(ContentPipelineManager.REFSET_COUNT, refset.getFsn() + " created");
		conceptCreator.outputRF2(Component.ComponentType.SIMPLE_REFSET_MEMBER, rm.toRF2());
	}

}
