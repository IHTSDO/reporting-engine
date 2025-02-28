package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class ImportNpuConcepts extends ContentPipelineManager implements NpuScriptConstants {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ImportNpuConcepts.class);

	private static final boolean PRODUCE_LIST_OF_PARTS = false;

	private static final int FILE_IDX_NPU_PARTS_MAP_BASE_FILE = 1;

	Map<String, NpuDetail> npuDetailsMap = new HashMap<>();

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
				"npu_code, shortDefinition, system, component, kindOfProperty, proc, unit, specialty, contextDependent, group, scaleType, active, , ",
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
		namespace = "1003000";
	}

	@Override
	protected String getContentType() {
		return "Observable";
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		importNpuConcepts();
		importNpuDetail();
		loadPanels();

		if (PRODUCE_LIST_OF_PARTS) {
			produceListOfParts();
		}
	}

	private void importNpuDetail() throws TermServerScriptException {
		//Read in tab delimited FILE_IDX_NPU_DETAIL and create NpuDetail objects
		try {
			List<String> lines = FileUtils.readLines(getInputFile(FILE_IDX_NPU_DETAIL), StandardCharsets.UTF_8);
			boolean isHeader = true;
			for (String line : lines) {
				if (isHeader) {
					isHeader = false;
					continue;
				}
				String[] columns = line.split("\t", -1);
				NpuDetail npuDetail = NpuDetail.parse(columns);
				npuDetailsMap.put(npuDetail.getNpuCode(), npuDetail);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read NPU detail file", e);
		}
	}

	private void produceListOfParts() {
		Set<Part> parts = new TreeSet<>();
		for (Map.Entry<String, ExternalConcept> entry : externalConceptMap.entrySet()) {
			NpuConcept npuConcept = (NpuConcept) entry.getValue();
			parts.addAll(npuConcept.getParts());
		}

		//Write out the parts to a local file, tab delimited
		String[] columnHeadings = new String[] {
				"PartNum\tPart Display\tPart Category"
		};

		LOGGER.info("Writing parts list to parts_list.txt");
		try (FileWriter writer = new FileWriter("parts_list.txt")) {
			// Write column headings
			writer.write(String.join("\t", columnHeadings) + "\n");

			// Write each part
			for (Part part : parts) {
				writer.write(String.join("\t",
						part.getPartNumber(),
						part.getPartNumber(),
						part.getPartCategory()
				) + "\n");
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write parts list", e);
		}
		System.exit(0);
	}

	private void importNpuConcepts() throws TermServerScriptException {
		ObjectMapper mapper = new XmlMapper();
		TypeReference<List<NpuConcept>> listType = new TypeReference<>(){};
		try {
			FileInputStream is = FileUtils.openInputStream(getInputFile(FILE_IDX_NPU_FULL));
			List<NpuConcept> npuConcepts = mapper.readValue(is, listType);
			externalConceptMap = npuConcepts.stream()
				.collect(Collectors
						.toMap(NpuConcept::getExternalIdentifier,
								c -> c,
								(first, second) -> ((NpuConcept)second).getEffectiveTo() == null ? second : first));
				} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
		LOGGER.info("Loaded {} NPU Concepts", externalConceptMap.size());
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
	protected List<String> getExternalConceptsToModel() throws TermServerScriptException {
		try {
			return FileUtils.readLines(getInputFile(FILE_IDX_NPU_TECH_PREVIEW_CONCEPTS), "UTF-8");
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to read NPU codes from file", e);
		}
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
