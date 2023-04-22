package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

public class AttributePartMapManager {
	
	private int TAB_RF2_PART_MAP_NOTES = TermServerScript.QUATERNARY_REPORT;

	private TermServerScript ts;
	private GraphLoader gl;
	private Map<String, LoincPart> loincParts;
	private Map<String, Map<Concept, Set<RelationshipTemplate>>> loincPartToAttributeMap;
	private Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	private int size = 0;
	private Concept component;
	
	public AttributePartMapManager (TermServerScript ts, Map<String, LoincPart> loincParts) {
		this.ts = ts;
		this.gl = ts.getGraphLoader();
		this.loincParts = loincParts;
	}

	public RelationshipTemplate getPartMappedAttributeForType(int idxTab, String loincNum, String loincPartNum, Concept attributeType) throws TermServerScriptException {
		RelationshipTemplate rt = null;
		if (!containsMappingForLoincPartNum(loincPartNum)) {
			ts.report(idxTab,
					loincNum,
					loincPartNum,
					"No attribute mapping available");
		} else {
			Map<Concept, Set<RelationshipTemplate>> typeAttributeMap = getTypeAttributeMap(loincPartNum);
			if (!typeAttributeMap.containsKey(attributeType)) {
				ts.report(idxTab,
						loincNum,
						loincPartNum,
						"Attribute mappings available, but not for attribute type",
						attributeType);
			} else {
				Set<RelationshipTemplate> attributes = typeAttributeMap.get(attributeType);
				if (attributes.size() > 1) {
					ts.report(idxTab,
							loincNum,
							loincPartNum,
							"Multiple attribute values available for mapping. Choosing at random!",
							attributeType);
				}
				rt = attributes.iterator().next();
			}
		}
		return rt;
	}
	
	Map<Concept, Set<RelationshipTemplate>> getTypeAttributeMap(String loincPartNum) {
		Map<Concept, Set<RelationshipTemplate>> typeAttributeMap = loincPartToAttributeMap.get(loincPartNum);
		if (typeAttributeMap == null) {
			typeAttributeMap = new HashMap<>();
			loincPartToAttributeMap.put(loincPartNum, typeAttributeMap);
		}
		return typeAttributeMap;
	}
	
	public void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException {
		loincPartToAttributeMap = new HashMap<>();
		populateKnownMappings();
		int lineNum = 0;
		try {
			int successfullTypeReplacement = 0;
			int successfullValueReplacement = 0;
			int unsuccessfullTypeReplacement = 0;
			int unsuccessfullValueReplacement = 0;
			TermServerScript.info("Loading Part Attribute Map: " + attributeMapFile);
			try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum > 1) {
						String[] items = line.split("\t");
						//Skip the row if it's not active
						if (items[2].equals("0")) {
							continue;
						}
						String partNum = items[6];
						Concept attributeType = gl.getConcept(items[7]);
						Concept attributeValue = gl.getConcept(items[5]);
						
						if (!attributeType.isActive()) {
							String hardCodedIndicator = " hardcoded";
							Concept replacementType = knownReplacementMap.get(attributeType);
							if (replacementType == null) {
								hardCodedIndicator = "";
								replacementType = ts.getReplacementSafely(TAB_RF2_PART_MAP_NOTES, partNum, attributeType, false);
							} 
							String replacementMsg = replacementType == null ? " no replacement available." : hardCodedIndicator + " replaced with " + replacementType;
							if (replacementType == null) unsuccessfullTypeReplacement++; 
								else successfullTypeReplacement++;
							ts.report(TAB_RF2_PART_MAP_NOTES, partNum, "Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);
							if (replacementType != null) {
								attributeType = replacementType;
							}
						}
						
						LoincPart part = loincParts.get(partNum);
						String partName = part == null ? "Unlisted" : part.getPartName();
						String partStatus = part == null ? "Unlisted" : part.getStatus().name();
					
						if (!attributeValue.isActive()) {
							String hardCodedIndicator = " hardcoded";
							Concept replacementValue = knownReplacementMap.get(attributeValue);
							if (replacementValue == null) {
								hardCodedIndicator = "";
								replacementValue = ts.getReplacementSafely(TAB_RF2_PART_MAP_NOTES, partNum, attributeValue, false);
							}
							String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
							if (replacementValue == null) unsuccessfullValueReplacement++; 
							else successfullValueReplacement++;
							String prefix = replacementValue == null ? "* " : "";
							ts.report(TAB_RF2_PART_MAP_NOTES, partNum, partName, partStatus, prefix + "Mapped to" + hardCodedIndicator + " inactive value: " + attributeValue + replacementMsg);
							if (replacementValue != null) {
								attributeValue = replacementValue;
							}
						}
						addAttributeMapping(partNum, partName, partStatus, new RelationshipTemplate(attributeType, attributeValue));
					}
				}
			}
			
			TermServerScript.info("Populated map of " + size + " LOINC parts to attributes");
			ts.report(TAB_RF2_PART_MAP_NOTES, "");
			ts.report(TAB_RF2_PART_MAP_NOTES, "successfullTypeReplacement",successfullTypeReplacement);
			ts.report(TAB_RF2_PART_MAP_NOTES, "unsuccessfullTypeReplacement",unsuccessfullTypeReplacement);
			ts.report(TAB_RF2_PART_MAP_NOTES, "successfullValueReplacement",successfullValueReplacement);
			ts.report(TAB_RF2_PART_MAP_NOTES, "unsuccessfullValueReplacement",unsuccessfullValueReplacement);
		} catch (Exception e) {
			throw new TermServerScriptException("At line " + lineNum, e);
		}
	}

	private void addAttributeMapping(String partNum, String partName, String partStatus, RelationshipTemplate attribute) throws TermServerScriptException {
		Map<Concept, Set<RelationshipTemplate>> typeAttributeMap = getTypeAttributeMap(partNum);
		Set<RelationshipTemplate> attributesForType = typeAttributeMap.get(attribute.getType());
		if (attributesForType != null) {
			ts.report(TAB_RF2_PART_MAP_NOTES, partNum, partName, partStatus, "Multiple attributes for " + partNum + " " + attribute.getType().toStringPref());
		} else {
			attributesForType = new HashSet<>();
			typeAttributeMap.put(attribute.getType(), attributesForType);
		}
		attributesForType.add(attribute);
		size++;
	}

	private void populateKnownMappings() throws TermServerScriptException {
		component = gl.getConcept("246093002 |Component (attribute)|");
		
		knownReplacementMap.put(gl.getConcept("720309005 |Immunoglobulin G antibody to Streptococcus pneumoniae 43 (substance)|"), gl.getConcept("767402003 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 43 (substance)|"));
		knownReplacementMap.put(gl.getConcept("720308002 |Immunoglobulin G antibody to Streptococcus pneumoniae 34 (substance)|"), gl.getConcept("767408004 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 34 (substance)|"));
		knownReplacementMap.put(gl.getConcept("54708003 |Extended zinc insulin (substance)|"), gl.getConcept("10329000 |Zinc insulin (substance)|"));
		knownReplacementMap.put(gl.getConcept("409258004 |Hydroxocobalamin (substance)|"), gl.getConcept("1217427007 |Aquacobalamin (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("27192005 |Aminosalicylic acid (substance)|"), gl.getConcept("255666002 |Para-aminosalicylic acid (substance)|"));
		knownReplacementMap.put(gl.getConcept("250428009 |Substance with antimicrobial mechanism of action (substance)|"), gl.getConcept("419241000 |Substance with antibacterial mechanism of action (substance)|"));
		knownReplacementMap.put(gl.getConcept("119306004 |Drain device specimen (specimen)|"), gl.getConcept("1003707004 |Drain device submitted as specimen (specimen)|"));
	}

	public boolean containsMappingForLoincPartNum(String loincPartNum) {
		return loincPartToAttributeMap.containsKey(loincPartNum);
	}

	public RelationshipTemplate get(int tabIdx, String loincNum, String loincPartNum) throws TermServerScriptException {
		//Without specifying the type, first see if there's only one (easy pick)
		//Or otherwise use the Component
		Map<Concept, Set<RelationshipTemplate>> typeAttributeMap = getTypeAttributeMap(loincPartNum);
		
		Concept attributeType = typeAttributeMap.keySet().iterator().next();
		if (typeAttributeMap.size() > 1) {
			attributeType = component;
		}
		
		return getPartMappedAttributeForType(tabIdx, loincNum, loincPartNum, attributeType);
	}
}
