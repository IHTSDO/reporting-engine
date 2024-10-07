package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.*;

public class ImportLoincTerms extends LoincScript implements LoincScriptConstants {

	private static final String COMMON_LOINC_COLUMNS = "COMPONENT, PROPERTY, TIME_ASPCT, SYSTEM, SCALE_TYP, METHOD_TYP, CLASS, CLASSTYPE, VersionLastChanged, CHNG_TYPE, STATUS, STATUS_REASON, STATUS_TEXT, ORDER_OBS, LONG_COMMON_NAME, COMMON_TEST_RANK, COMMON_ORDER_RANK, COMMON_SI_TEST_RANK, PanelType, , , , , ";
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f1 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2023.tsv"  
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Primary.tsv"
	//-f3 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\LoincTable\Loinc.csv"
	//-f5 "G:\My Drive\018_Loinc\2023\Loinc_Detail_Type_1_2.75_Active_Lab_NonVet.tsv"
	
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

		super.postInit(tabNames, columnHeadings);
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
			if (conceptSufficientlyModeled("Observable", loincNum, templatedConcept)) {
				successfullyModelled.add(templatedConcept);
			}
		}

		for (String panelLoincNum : panelLoincNums) {
			LoincTemplatedConcept templatedConcept = doPanelModeling(panelLoincNum);
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
		LoincTemplatedConceptPanel templatedPanelConcept = LoincTemplatedConceptPanel.create(panelTerm);
		validateTemplatedConcept(templatedPanelConcept);
		return templatedPanelConcept;
	}
	
	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return switch (externalConcept.getProperty()) {
			case "ArVRat", "CRat", "MRat", "RelTime", "SRat", "Time", "Vel", "VRat" -> LoincTemplatedConceptWithProcess.create(externalConcept);
			case "NFr", "MFr", "CFr", "AFr",  "SFr", "VFr" -> LoincTemplatedConceptWithRelative.create(externalConcept);
			case "ACnc", "Angle", "CCnc", "CCnt", "Diam", "EntCat", "EntLen", "EntMass", "EntNum", "EntSub",
			     "EntVol", "LaCnc", "LnCnc", "LsCnc", "Mass", "MCnc", "MCnt", "MoM", "Naric", "NCnc",
			     "Osmol", "PPres", "Pres", "PrThr", "SCnc", "SCnt", "Sub", "Titr", "Visc" ->
					LoincTemplatedConceptWithComponent.create(externalConcept);
			case "Aper", "Color", "Rden", "Source","SpGrav","Temp" ->
					LoincTemplatedConceptWithDirectSite.create(externalConcept);
			case "MRto", "Ratio", "SRto" -> LoincTemplatedConceptWithRatio.create(externalConcept);
			case "Anat", "DistWidth", "EntMCnc", "EntMeanVol", "ID", "Morph",
			     "Prid", "Type", "Vol" -> LoincTemplatedConceptWithInheres.create(externalConcept);
			case "Susc" -> LoincTemplatedConceptWithSusceptibility.create(externalConcept);
			default -> TemplatedConceptNull.create(externalConcept);
		};
	}

	public TemplatedConcept populateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		TemplatedConcept templatedConcept = getAppropriateTemplate(externalConcept);
		if (templatedConcept != null) {
			templatedConcept.populateTemplate();
		} else if (externalConcept.isHighestUsage()) {
			//This is a highest usage term which is out of scope
			incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS, "Highest Usage Out of Scope");
		}
		return templatedConcept;
	}

}
