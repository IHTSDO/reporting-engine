package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConceptWithDefaultMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class ImportNpuConcepts extends ContentPipelineManager {

	private List<String> panelNpuNums;
	
	private static final int FILE_IDX_NPU_PARTS_MAP_BASE_FILE = 1;
	
	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_MAP_ME,
			TAB_IMPORT_STATUS,
			TAB_IOI,
			TAB_STATS};
	
	public static void main(String[] args) throws TermServerScriptException {
		new ImportNpuConcepts().ingestExternalContent(args);
	}

	protected String[] getTabNames() {
		return tabNames;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Item, Info, Details, Foo, Bar, What, Goes, Here?",
				"NpuPartNum, NpuPartName, PartType, ColumnName, Part Status, SCTID, FSN, Priority Index, Usage Count, Top Priority Usage, Mapping Notes,",
				"NpuNum, Item of Special Interest, NpuName, Issues, details",
				"NpuNum, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, ADD_COMMON_COLUMNS",
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count,Top Priority Usage, Higest Rank, HighestUsageCount",
				"Concept, FSN, SemTag, Severity, Action, NpuNum, Descriptions, Expression, Status, , ",
				"Category, NpuNum, Detail, , , "
		};

		super.postInit(tabNames, columnHeadings, false);
		scheme = gl.getConcept(SCTID_NPU_SCHEMA);
		externalContentModule = SCTID_NPU_EXTENSION_MODULE;
		namespace = "1010000";
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		importNpuConcepts();
		loadPanels();
	}
	
	private void importNpuConcepts() throws TermServerScriptException {
		ObjectMapper mapper = new XmlMapper();
		TypeReference<List<NpuConcept>> listType = new TypeReference<List<NpuConcept>>(){};
		try {
			FileInputStream is = FileUtils.openInputStream(getInputFile());
			List<NpuConcept> npuConcepts = mapper.readValue(is, listType);
			externalConceptMap = npuConcepts.stream()
				.collect(Collectors
						.toMap(NpuConcept::getExternalIdentifier, 
								c -> c,
								(first, second) -> {
									//One of them will not have an end time
									return ((NpuConcept)second).getEffectiveTo() == null ? second : first;
								}));
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
	}

	private void loadPanels() {
		//Populate panelNpuNums from file
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		attributePartMapManager = new NpuAttributePartMapManager(this, partMap, partMapNotes);
		attributePartMapManager.populatePartAttributeMap(getInputFile(FILE_IDX_NPU_PARTS_MAP_BASE_FILE));
		NpuTemplatedConcept.initialise(this);
	}

	@Override
	protected Set<TemplatedConcept> doModeling() throws TermServerScriptException {
		Set<TemplatedConcept> successfullyModelledConcepts = new HashSet<>();
		for (String npuNum : getExternalConceptMap().keySet()) {
			TemplatedConcept templatedConcept = doModeling(npuNum);
			checkConceptSufficientlyModeled("Observable", npuNum, templatedConcept, successfullyModelledConcepts);
		}

		for (String panelNpuNum : panelNpuNums) {
			NpuTemplatedConcept templatedConcept = doPanelModeling(panelNpuNum);
			checkConceptSufficientlyModeled("Panel", panelNpuNum, templatedConcept, successfullyModelledConcepts);
		}

		return successfullyModelledConcepts;
	}

	private TemplatedConcept doModeling(String npuNum) throws TermServerScriptException {
		if (!confirmExternalIdentifierExists(npuNum) || 
				containsObjectionableWord(getExternalConcept(npuNum))) {
			return null;
		}

		//Is this a npunum that's being maintained manually?  Return what is already there if so.
		if (MANUALLY_MAINTAINED_ITEMS.containsKey(npuNum)) {
			TemplatedConcept tc = TemplatedConceptWithDefaultMap.create(getNpuConcept(npuNum));
			tc.setConcept(gl.getConcept(MANUALLY_MAINTAINED_ITEMS.get(npuNum)));
			return tc;
		}

		NpuTemplatedConcept templatedConcept = getAppropriateTemplate(getExternalConcept(npuNum));
		templatedConcept.populateTemplate();
		validateTemplatedConcept(templatedConcept);
		return templatedConcept;
	}
	


	private NpuTemplatedConcept doPanelModeling(String panelNpuNum) throws TermServerScriptException {
		//Don't do objectionable word check on panels - 'panel' is our only current objectionable word!
		if (!confirmExternalIdentifierExists(panelNpuNum)) {
			return null;
		}

		ExternalConcept panelTerm = getNpuConcept(panelNpuNum);
		NpuTemplatedConceptPanel templatedPanelConcept = NpuTemplatedConceptPanel.create(panelTerm);
		validateTemplatedConcept(templatedPanelConcept);
		return templatedPanelConcept;
	}
	
	private NpuConcept getNpuConcept(String externalIdentifier) {
		return (NpuConcept)externalConceptMap.get(externalIdentifier);
	}

	@Override
	public NpuTemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return NpuTemplatedConceptWithComponent.create(externalConcept);
	}

	public NpuTemplatedConcept populateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		NpuTemplatedConcept templatedConcept = getAppropriateTemplate(externalConcept);
		if (templatedConcept != null) {
			templatedConcept.populateTemplate();
		} else if (externalConcept.isHighestUsage()) {
			//This is a highest usage term which is out of scope
			incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS, "Highest Usage Out of Scope");
		}
		return templatedConcept;
	}

	@Override
	protected Set<String> getObjectionableWords() {
		return new HashSet<>();  //No objections here
	}

	@Override
	public List<String> getMappingsAllowedAbsent() {
		return new ArrayList<>();  //Not yet expecting to allow missin mappings
	}

	@Override
	protected String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts) {
		return new String[0];
	}

}
