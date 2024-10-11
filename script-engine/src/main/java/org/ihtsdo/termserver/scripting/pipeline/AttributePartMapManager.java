package org.ihtsdo.termserver.scripting.pipeline;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

public abstract class AttributePartMapManager {

	private static final int NOT_SET = -1;

	protected ContentPipelineManager cpm;
	protected GraphLoader gl;
	protected Map<String, Part> parts;
	protected Map<String, RelationshipTemplate> partToAttributeMap = new HashMap<>();
	protected Map<String, List<Concept>> hardCodedMappings = new HashMap<>();
	protected Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	protected Map<Concept, Concept> hardCodedTypeReplacementMap = new HashMap<>();
	protected final Map<String, String> partMapNotes;

	protected int unsuccessfullTypeReplacement = 0;
	protected int successfullTypeReplacement = 0;
	protected int successfullValueReplacement = 0;
	protected int unsuccessfullValueReplacement = 0;
	protected int lexicallyMatchingMapReuse = 0;
	
	protected AttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> loincParts, Map<String, String> partMapNotes) {
		this.cpm = cpm;
		this.gl = cpm.getGraphLoader();
		this.parts = loincParts;
		this.partMapNotes = partMapNotes;
	}

	protected abstract void populateKnownMappings() throws TermServerScriptException;

	public List<RelationshipTemplate> getPartMappedAttributeForType(int idxTab, String externalIdentifier, String partNum, Concept attributeType) throws TermServerScriptException {
		if (hardCodedMappings.containsKey(partNum)) {
			List<RelationshipTemplate> mappings = new ArrayList<>();
			for (Concept attributeValue : hardCodedMappings.get(partNum)) {
				mappings.add(new RelationshipTemplate(attributeType, attributeValue));
			}
			return mappings;
		} else if (containsMappingForLoincPartNum(partNum)) {
			RelationshipTemplate rt = partToAttributeMap.get(partNum).clone();
			rt.setType(attributeType);
			return List.of(rt);
		} else if (idxTab != NOT_SET && !cpm.getMappingsAllowedAbsent().contains(partNum)) {
			//Some special rules exist for certain LOINC parts, so we don't need to report if we have one of those.
			String loincPartStr = parts.get(partNum) == null ? "Loinc Part Not Known - " + partNum : parts.get(partNum).toString();
			cpm.report(idxTab,
					externalIdentifier,
					ContentPipelineManager.getSpecialInterestIndicator(externalIdentifier),
					cpm.getExternalConcept(externalIdentifier).getLongDisplayName(),
					"No attribute mapping available",
					loincPartStr);
			cpm.addMissingMapping(partNum, externalIdentifier);
		}
		return new ArrayList<>();
	}
	
	public abstract void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException; 

	public Concept replaceValueIfRequired(List<String> mappingNotes, Concept attributeValue) {

		if (!attributeValue.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementValue = knownReplacementMap.get(attributeValue);
			if (replacementValue == null) {
				hardCodedIndicator = "";
				replacementValue = cpm.getReplacementSafely(mappingNotes, attributeValue, false);
			}
			
			String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
			if (replacementValue == null) unsuccessfullValueReplacement++;
			else successfullValueReplacement++;
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
			if (replacementType == null) unsuccessfullTypeReplacement++;
				else successfullTypeReplacement++;
			mappingNotes.add("Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);

			if (replacementType != null) {
				attributeType = replacementType;
			}
		}
		return attributeType;
	}
	

	public boolean containsMappingForLoincPartNum(String loincPartNum) {
		return partToAttributeMap.containsKey(loincPartNum);
	}

	public Set<String> getAllMappedLoincPartNums() {
		return partToAttributeMap.keySet();
	}

	protected abstract void populateHardCodedMappings() throws TermServerScriptException;
}
