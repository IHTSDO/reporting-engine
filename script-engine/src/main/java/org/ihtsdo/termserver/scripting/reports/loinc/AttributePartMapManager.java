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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttributePartMapManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributePartMapManager.class);

	private static int NOT_SET = -1;
	
	private int TAB_RF2_PART_MAP_NOTES = TermServerScript.PRIMARY_REPORT;

	private TermServerScript ts;
	private GraphLoader gl;
	private Map<String, LoincPart> loincParts;
	private Map<String, RelationshipTemplate> loincPartToAttributeMap;
	private Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	private Map<Concept, Concept> hardCodedTypeReplacementMap = new HashMap<>();
	
	private int unsuccessfullTypeReplacement = 0;
	private int successfullTypeReplacement = 0;
	private int successfullValueReplacement = 0;
	private int unsuccessfullValueReplacement = 0;
	
	public AttributePartMapManager (TermServerScript ts, Map<String, LoincPart> loincParts) {
		this.ts = ts;
		this.gl = ts.getGraphLoader();
		this.loincParts = loincParts;
		
	}
	
	public static void validatePartAttributeMap(GraphLoader gl, File attributeMapFile) throws TermServerScriptException {
		int lineNum = 0;
		int failureCount = 0;
		try {
			LOGGER.info("Validating Part / Attribute Map Base File: " + attributeMapFile);
			try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum > 1) {
						String[] items = line.split("\t");
						Concept attributeValue = gl.getConcept(items[4], false, false);
						if (attributeValue == null) {
							LOGGER.warn("Part / Attribute Map Base File contains unknown concept " + items[4] + " at line " + lineNum);
							failureCount++;
						}
					}
				}
			} 
		} catch (Exception e) {
			throw new TermServerScriptException("Failure while reading " + attributeMapFile, e);
		}
		
		if (failureCount > 0) {
			throw new TermServerScriptException(failureCount + " failures while reading " + attributeMapFile);
		}
	}

	public RelationshipTemplate getPartMappedAttributeForType(int idxTab, String loincNum, String loincPartNum, Concept attributeType) throws TermServerScriptException {
		RelationshipTemplate rt = null;
		if (containsMappingForLoincPartNum(loincPartNum)) {
			rt = loincPartToAttributeMap.get(loincPartNum).clone();
			rt.setType(attributeType);
		} else {
			ts.report(idxTab,
					loincNum,
					loincPartNum,
					"No attribute mapping available");
		}
		return rt;	
	}
	
	public void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException {
		loincPartToAttributeMap = new HashMap<>();
		populateKnownMappings();
		int lineNum = 0;
		Set<String> partsSeen = new HashSet<>();
		try {
			LOGGER.info("Loading Part / Attribute Map Base File: " + attributeMapFile);
			try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum > 1) {
						String[] items = line.split("\t");
						String partNum = items[1];
						//Have we seen this part before?  Map should now be unique
						if (partsSeen.contains(partNum)) {
							throw new TermServerScriptException("Part / Attribute BaseFile contains duplicate entry for " + partNum);
						}
						partsSeen.add(partNum);
						Concept attributeValue = gl.getConcept(items[4], false, true);
						
						LoincPart part = loincParts.get(partNum);
						String partName = part == null ? "Unlisted" : part.getPartName();
						String partStatus = part == null ? "Unlisted" : part.getStatus().name();
						attributeValue = replaceValueIfRequired(TAB_RF2_PART_MAP_NOTES, attributeValue, partNum, partName, partStatus);
						loincPartToAttributeMap.put(partNum, new RelationshipTemplate(null, attributeValue));
					}
				}
			}
			
			LOGGER.info("Populated map of " + loincPartToAttributeMap.size() + " LOINC parts to attributes");
			ts.report(TAB_RF2_PART_MAP_NOTES, "");
			ts.report(TAB_RF2_PART_MAP_NOTES, "successfullTypeReplacement",successfullTypeReplacement);
			ts.report(TAB_RF2_PART_MAP_NOTES, "unsuccessfullTypeReplacement",unsuccessfullTypeReplacement);
			ts.report(TAB_RF2_PART_MAP_NOTES, "successfullValueReplacement",successfullValueReplacement);
			ts.report(TAB_RF2_PART_MAP_NOTES, "unsuccessfullValueReplacement",unsuccessfullValueReplacement);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + attributeMapFile + " at line " + lineNum, e);
		}
	}

	public Concept replaceValueIfRequired(int tabIdx, Concept attributeValue, String partNum,
			String partName, String partStatus) throws TermServerScriptException {
		
		
		if (!attributeValue.isActive()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementValue = knownReplacementMap.get(attributeValue);
			if (replacementValue == null) {
				hardCodedIndicator = "";
				replacementValue = ts.getReplacementSafely(tabIdx, partNum, attributeValue, false);
			}
			
			if (tabIdx != NOT_SET) {
				String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
				if (replacementValue == null) unsuccessfullValueReplacement++; 
				else successfullValueReplacement++;
				String prefix = replacementValue == null ? "* " : "";
				ts.report(tabIdx, partNum, partName, partStatus, prefix + "Mapped to" + hardCodedIndicator + " inactive value: " + attributeValue + replacementMsg);
			}
			
			if (replacementValue != null) {
				attributeValue = replacementValue;
			}
		}
		return attributeValue;
	}

	public Concept replaceTypeIfRequired(int tabIdx, Concept attributeType, String partNum) throws TermServerScriptException {
		if (hardCodedTypeReplacementMap.containsKey(attributeType)) {
			attributeType = hardCodedTypeReplacementMap.get(attributeType);
		}
		
		if (!attributeType.isActive()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementType = knownReplacementMap.get(attributeType);
			if (replacementType == null) {
				hardCodedIndicator = "";
				replacementType = ts.getReplacementSafely(TAB_RF2_PART_MAP_NOTES, partNum, attributeType, false);
			} 
			String replacementMsg = replacementType == null ? " no replacement available." : hardCodedIndicator + " replaced with " + replacementType;
			if (tabIdx != NOT_SET) {
				if (replacementType == null) unsuccessfullTypeReplacement++; 
					else successfullTypeReplacement++;
				ts.report(tabIdx, partNum, "Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);
			}
			
			if (replacementType != null) {
				attributeType = replacementType;
			}
		}
		return attributeType;
	}

	private void populateKnownMappings() throws TermServerScriptException {
		knownReplacementMap.put(gl.getConcept("720309005 |Immunoglobulin G antibody to Streptococcus pneumoniae 43 (substance)|"), gl.getConcept("767402003 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 43 (substance)|"));
		knownReplacementMap.put(gl.getConcept("720308002 |Immunoglobulin G antibody to Streptococcus pneumoniae 34 (substance)|"), gl.getConcept("767408004 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 34 (substance)|"));
		knownReplacementMap.put(gl.getConcept("54708003 |Extended zinc insulin (substance)|"), gl.getConcept("10329000 |Zinc insulin (substance)|"));
		knownReplacementMap.put(gl.getConcept("409258004 |Hydroxocobalamin (substance)|"), gl.getConcept("1217427007 |Aquacobalamin (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("27192005 |Aminosalicylic acid (substance)|"), gl.getConcept("255666002 |Para-aminosalicylic acid (substance)|"));
		knownReplacementMap.put(gl.getConcept("250428009 |Substance with antimicrobial mechanism of action (substance)|"), gl.getConcept("419241000 |Substance with antibacterial mechanism of action (substance)|"));
		knownReplacementMap.put(gl.getConcept("119306004 |Drain device specimen (specimen)|"), gl.getConcept("1003707004 |Drain device submitted as specimen (specimen)|"));
	
		hardCodedTypeReplacementMap.put(gl.getConcept("410670007 |Time|"), gl.getConcept("370134009 |Time aspect|"));
	}

	public boolean containsMappingForLoincPartNum(String loincPartNum) {
		return loincPartToAttributeMap.containsKey(loincPartNum);
	}

}
