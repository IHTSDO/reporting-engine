package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincRf2MapExpansion extends LoincScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincRf2MapExpansion.class);

	public static final String TAB_RF2_MAP = "RF2 Map";
	
	private static String[] tabNames = new String[] { TAB_RF2_MAP };
	
	private static int REF_IDX_MAP_TARGET = 6;
	private static int REF_IDX_ATTRIB = 7;
	
	AttributePartMapManager attributePartManager;
	
	Map<String, Set<String>> partToLoincTermMap;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
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
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"id, active, refCompId, PT, replacementValue, mapTarget, Usage Count, PartName, PartType, attribute, attribtPT, replacementType, , , "
		};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void runReport() throws TermServerScriptException, InterruptedException, IOException {
		loadLoincParts();
		attributePartManager = new AttributePartMapManager(this, loincParts, null);
		loadLoincDetail();
		expandRf2Map(PRIMARY_REPORT, getInputFile(FILE_IDX_LOINC_PARTS_MAP_BASE_FILE));
	}

	private void expandRf2Map(int tabIdx, File attributeMapFile) throws IOException, TermServerScriptException {
		int lineNum = 0;
		TermServerScript.info("Loading Part Attribute Map: " + attributeMapFile);
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
		// TODO Auto-generated method stub
		Concept value = gl.getConcept(items[REF_IDX_REFCOMPID], false, false);
		LoincPart part = loincParts.get(items[REF_IDX_MAP_TARGET]);
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
}
