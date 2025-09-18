package org.ihtsdo.termserver.scripting.pipeline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AttributePartMapManager implements ContentPipeLineConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributePartMapManager.class);
	private static final String MAP_IMPORT = "Map Import";
	private static final int NOT_SET = -1;

	private static final int IDX_PART_NUM = 0;
	private static final int IDX_STATUS = 7;
	private static final int IDX_NO_MAP = 6;
	private static final int IDX_TARGET = 2;

	protected ContentPipelineManager cpm;
	protected GraphLoader gl;
	protected Map<String, Part> parts;
	protected Map<String, RelationshipTemplate> partToAttributeMap = new HashMap<>();
	protected Map<String, List<Concept>> hardCodedMappings = new HashMap<>();
	protected Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	protected Map<Concept, Concept> hardCodedTypeReplacementMap = new HashMap<>();
	protected final Map<String, String> partMapNotes;

	protected boolean allowStatusMapped = false;
	
	protected AttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> parts, Map<String, String> partMapNotes) {
		this.cpm = cpm;
		this.gl = cpm.getGraphLoader();
		this.parts = parts;
		this.partMapNotes = partMapNotes;
	}

	protected abstract void populateKnownMappings() throws TermServerScriptException;

	public List<RelationshipTemplate> getPartMappedAttributeForType(TemplatedConcept tc, String partNum, Concept attributeType) throws TermServerScriptException {
		if (SnomedUtils.isEmpty(partNum)) {
			//Can't look up an unspecified part.
			//In the case of, eg NPU Unit not being specified, this is fine.
		} else if (hardCodedMappings.containsKey(partNum)) {
			List<RelationshipTemplate> mappings = new ArrayList<>();
			for (Concept attributeValue : hardCodedMappings.get(partNum)) {
				mappings.add(new RelationshipTemplate(attributeType, attributeValue));
			}
			return mappings;
		} else if (containsMappingForPartNum(partNum)) {
			RelationshipTemplate rt = partToAttributeMap.get(partNum).clone();
			rt.setType(attributeType);
			return List.of(rt);
		} else if (!cpm.getMappingsAllowedAbsent().contains(partNum)) {
			//Some special rules exist for certain parts, so we don't need to report if we have one of those.
			String partStr = parts.get(partNum) == null ? "Part Not Known - " + partNum : parts.get(partNum).toString();
			tc.getConcept().addIssue("No attribute mapping available fo" + partStr);
			cpm.addMissingMapping(partNum, tc.getExternalIdentifier());
		}
		return new ArrayList<>();
	}
	
	public void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException {
		// Output format from Snap2SNOMED is expected to be:
		// Source code[0]   Source display  Status  PartTypeName    Target code[4]  Target display  Relationship type code  Relationship type display   No map flag[8] Status[9]
		populateKnownMappings();
		populateHardCodedMappings();
		int lineNum = 0;
		Set<String> partsSeen = new HashSet<>();
		List<String> mappingNotes = new ArrayList<>();

		try {
			LOGGER.info("Loading Part Attribute Map File: {}", attributeMapFile);
			try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (!line.isEmpty() && lineNum > 1) {
						processPartFileLine(line, partsSeen, mappingNotes);
					}
				}
			}
			LOGGER.info("Populated map of {} parts to attributes", partToAttributeMap.size());
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + attributeMapFile + " at line " + lineNum, e);
		}
	}

	private void processPartFileLine(String line, Set<String> partsSeen, List<String> mappingNotes) throws TermServerScriptException {
		String[] items = line.split("\t");
		String partNum = items[IDX_PART_NUM];

		if (partsSeen.contains(partNum)) {
			//Have we seen this part before?  Map should now be unique
			mappingNotes.add("Part / Attribute BaseFile contains duplicate entry for " + partNum);
		} else if (items[IDX_NO_MAP].equals("true")) {
			//And we can have items that report being mapped, but with 'no map' - warn about those.
			mappingNotes.add("Map indicates part mapped to 'No Map'");
		} else if (items[IDX_STATUS].equals("ACCEPTED") || (allowStatusMapped && items[IDX_STATUS].equals("MAPPED"))) {
			partsSeen.add(partNum);
			Concept attributeValue = gl.getConcept(items[IDX_TARGET], false, true);
			attributeValue = replaceValueIfRequired(mappingNotes, attributeValue);
			if (attributeValue != null && attributeValue.isActive()) {
				mappingNotes.add("Inactive concept");
			}
			partToAttributeMap.put(partNum, new RelationshipTemplate(null, attributeValue));
		} else if (items[IDX_STATUS].equals("UNMAPPED")) {
			//Skip this one without mentioning it
		} else {
			mappingNotes.add("Map indicates non-viable map status - " + items[IDX_STATUS]);
		}

		if (!mappingNotes.isEmpty()) {
			partMapNotes.put(partNum, String.join("\n", mappingNotes));
			mappingNotes.clear();
		}
	}

	public Concept replaceValueIfRequired(List<String> mappingNotes, Concept attributeValue) {

		if (!attributeValue.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementValue = knownReplacementMap.get(attributeValue);
			if (replacementValue == null) {
				hardCodedIndicator = "";
				replacementValue = cpm.getReplacementSafely(mappingNotes, attributeValue, false);
			}

			String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
			String successStr = replacementValue == null ? "Unsuccessful" : "Successful";
			cpm.incrementSummaryCount(MAP_IMPORT, successStr + " value replacement");
			String prefix = replacementValue == null ? "* " : "";
			mappingNotes.add(prefix + "Mapped to" + hardCodedIndicator + " inactive value: " + attributeValue + replacementMsg);

			if (replacementValue != null) {
				attributeValue = replacementValue;
			}
		}
		return attributeValue;
	}

	public Concept replaceTypeIfRequired(List<String> mappingNotes, Concept attributeType) {
		if (hardCodedTypeReplacementMap.containsKey(attributeType)) {
			attributeType = hardCodedTypeReplacementMap.get(attributeType);
		}

		if (!attributeType.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementType = knownReplacementMap.get(attributeType);
			if (replacementType == null) {
				hardCodedIndicator = "";
				replacementType = cpm.getReplacementSafely(mappingNotes, attributeType, false);
			}
			String replacementMsg = replacementType == null ? " no replacement available." : hardCodedIndicator + " replaced with " + replacementType;
			String successStr = replacementType == null ? "Unsuccessful" : "Successful";
			cpm.incrementSummaryCount(MAP_IMPORT, successStr + " type replacement");
			mappingNotes.add("Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);

			if (replacementType != null) {
				attributeType = replacementType;
			}
		}
		return attributeType;
	}

	public boolean containsMappingForPartNum(String loincPartNum) {
		return partToAttributeMap.containsKey(loincPartNum);
	}

	protected abstract void populateHardCodedMappings() throws TermServerScriptException;

	public void allowStatusMapped(boolean allowStatusMapped) {
		this.allowStatusMapped = allowStatusMapped;
	}
}
