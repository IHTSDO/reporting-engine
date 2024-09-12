package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincRf2MapExpansion extends LoincScript {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LoincRf2MapExpansion.class);


	public static final String TAB_RF2_MAP = "RF2 Map";
	
	private static final String[] tabNames = new String[] { TAB_RF2_MAP };
	
	private static final int REF_IDX_MAP_TARGET = 6;
	private static final int REF_IDX_ATTRIB = 7;
	
	LoincAttributePartMapManager attributePartManager;
	
	Map<String, Set<String>> partToLoincTermMap;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		LoincRf2MapExpansion report = new LoincRf2MapExpansion();
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

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"id, active, refCompId, PT, replacementValue, mapTarget, Usage Count, PartName, PartType, attribute, attribtPT, replacementType, , , "
		};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void runReport() throws TermServerScriptException, IOException {
		loadLoincParts();
		attributePartManager = new LoincAttributePartMapManager(this, partMap, null);
		loadLoincDetail();
		expandRf2Map(PRIMARY_REPORT, getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
	}

	private void expandRf2Map(int tabIdx, File attributeMapFile) throws IOException, TermServerScriptException {
		int lineNum = 0;
		LOGGER.info("Loading Part Attribute Map: {}", attributeMapFile);
		try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				lineNum++;
				if (lineNum > 1) {
					String[] items = line.split("\t");
					expandRf2Line(tabIdx, items);
				}
			}
		}
	}

	private void expandRf2Line(int tabIdx, String[] items) throws TermServerScriptException {
		Concept value = gl.getConcept(items[REF_IDX_REFCOMPID], false, false);
		LoincPart part = getLoincPart(items[REF_IDX_MAP_TARGET]);
		Concept type = gl.getConcept(items[REF_IDX_ATTRIB], false, false);
		report(tabIdx,
				items[IDX_ID],
				items[IDX_ACTIVE],
				items[REF_IDX_REFCOMPID],
				value == null ? "Unknown" : value.getPreferredSynonym(),
				value == null ? "N/A" : reportIfValueReplaced(value),
				items[REF_IDX_MAP_TARGET],
				getUsageCount(items[REF_IDX_MAP_TARGET]),
				part == null ? "Not Found" : part.getPartName(),
				part == null ? "Not Found" : part.getPartTypeName(),
				items[REF_IDX_ATTRIB],
				type == null ? "Not Found" : type.getPreferredSynonym(),
				type == null ? "N/A" : reportIfTypeReplaced(type)
				);
	}

	private int getUsageCount(String partNum) {
		if (partToLoincTermMap == null) {
			populatePartToLoincTermMap();
		}
		Set<String> loincNums = partToLoincTermMap.get(partNum);
		return loincNums == null ? NOT_SET : loincNums.size();
	}

	private void populatePartToLoincTermMap() {
		partToLoincTermMap = new HashMap<>();
		for (Map.Entry<String, Map<String, LoincDetail>> entry : loincDetailMap.entrySet()) {
			for (LoincDetail detail : entry.getValue().values()) {
				//Have we seen this partNum before
				Set<String> loincTerms = partToLoincTermMap.get(detail.getPartNumber());
				if (loincTerms == null) {
					loincTerms = new HashSet<>();
					partToLoincTermMap.put(detail.getPartNumber(), loincTerms);
				}
				loincTerms.add(entry.getKey());
			}
		}
	}

	private String reportIfValueReplaced(Concept value) throws TermServerScriptException {
		Concept replacementValue = attributePartManager.replaceValueIfRequired(null, value, null, null, null);
		//If this concept is inactive and we don't have a replacement, then we really need one
		if (!value.isActive() && replacementValue.equals(value)) {
			return "REQUIRED";
		}
		
		return replacementValue.equals(value) ? "" : replacementValue.toStringPref();
	}
	
	private String reportIfTypeReplaced(Concept value) throws TermServerScriptException {
		Concept replacementType = attributePartManager.replaceTypeIfRequired(null, value, null);
		return replacementType.equals(value) ? "" : replacementType.toStringPref();
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {

	}

	@Override
	protected void importPartMap() throws TermServerScriptException {

	}

	@Override
	protected Set<TemplatedConcept> doModeling() throws TermServerScriptException {
		return null;
	}

	@Override
	protected void reportMissingMappings(int tab) {
		throw new NotImplementedException();
	}

	@Override
	protected void doProposedModelComparison(TemplatedConcept tc) throws TermServerScriptException {
		throw new NotImplementedException();
	}
}
