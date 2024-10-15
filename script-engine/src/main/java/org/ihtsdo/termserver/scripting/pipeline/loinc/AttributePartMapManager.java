package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;


import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttributePartMapManager implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributePartMapManager.class);

	private static int NOT_SET = -1;

	private LoincScript ls;
	private GraphLoader gl;
	private Map<String, LoincPart> loincParts;
	private Map<String, RelationshipTemplate> loincPartToAttributeMap;
	private Map<String, List<Concept>> hardCodedMappings = new HashMap<>();
	private Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	private Map<Concept, Concept> hardCodedTypeReplacementMap = new HashMap<>();
	private final Map<String, String> partMapNotes;

	private int unsuccessfullTypeReplacement = 0;
	private int successfullTypeReplacement = 0;
	private int successfullValueReplacement = 0;
	private int unsuccessfullValueReplacement = 0;
	private int lexicallyMatchingMapReuse = 0;
	
	public AttributePartMapManager (LoincScript ls, Map<String, LoincPart> loincParts, Map<String, String> partMapNotes) {
		this.ls = ls;
		this.gl = ls.getGraphLoader();
		this.loincParts = loincParts;
		this.partMapNotes = partMapNotes;
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

		populateHardCodedMappings();
	}

	public List<RelationshipTemplate> getPartMappedAttributeForType(int idxTab, String loincNum, String loincPartNum, Concept attributeType) throws TermServerScriptException {
		if (hardCodedMappings.containsKey(loincPartNum)) {
			List<RelationshipTemplate> mappings = new ArrayList<>();
			for (Concept attributeValue : hardCodedMappings.get(loincPartNum)) {
				mappings.add(new RelationshipTemplate(attributeType, attributeValue));
			}
			return mappings;
		} else if (containsMappingForLoincPartNum(loincPartNum)) {
			RelationshipTemplate rt = loincPartToAttributeMap.get(loincPartNum).clone();
			rt.setType(attributeType);
			return List.of(rt);
		} else if (idxTab != NOT_SET ) {
			String loincPartStr = loincParts.get(loincPartNum) == null ? "Loinc Part Not Known - " + loincPartNum : loincParts.get(loincPartNum).toString();
			ls.report(idxTab,
					loincNum,
					ContentPipelineManager.getSpecialInterestIndicator(loincNum),
					ls.getLoincNum(loincNum).getLongCommonName(),
					"No attribute mapping available",
					loincPartStr);
			ls.addMissingMapping(loincPartNum, loincNum);
		}
		return new ArrayList<>();
	}
	
	public void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException {
		// Output format from Snap2SNOMED is expected to be:
		// Source code[0]   Source display  Status  PartTypeName    Target code[4]  Target display  Relationship type code  Relationship type display   No map flag[8] Status[9]
		loincPartToAttributeMap = new HashMap<>();
		populateKnownMappings();
		int lineNum = 0;
		Set<String> partsSeen = new HashSet<>();
		List<String> mappingNotes = new ArrayList<>();

		try {
			LOGGER.info("Loading Part / Attribute Map Base File: " + attributeMapFile);
			try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (!line.isEmpty() && lineNum > 1) {
						String[] items = line.split("\t");
						String partNum = items[0];
						//Do we expect to see a map here?  Snap2Snomed also outputs unmapped parts
						if (items[9].equals("UNMAPPED") ||
								(items[9].equals("DRAFT") && items[4].isEmpty())) {
							//Skip this one
						} else if (items[8].equals("true")) {
							//And we can have items that report being mapped, but with 'no map' - warn about those.
							mappingNotes.add("Map indicates part mapped to 'No Map'");
						} else if (items[9].equals("REJECTED")) {
							//And we can have items that report being mapped, but with 'no map' - warn about those.
							mappingNotes.add("Map indicates non-viable map - " + items[9]);
						} else if (partsSeen.contains(partNum)) {
							//Have we seen this part before?  Map should now be unique
							mappingNotes.add("Part / Attribute BaseFile contains duplicate entry for " + partNum);
						} else {
							partsSeen.add(partNum);
							Concept attributeValue = gl.getConcept(items[4], false, true);
							LoincPart part = loincParts.get(partNum);
							String partName = part == null ? "Unlisted" : part.getPartName();
							String partStatus = part == null ? "Unlisted" : part.getStatus().name();
							attributeValue = replaceValueIfRequired(mappingNotes, attributeValue, partNum, partName, partStatus);
							if (attributeValue != null && attributeValue.isActive()) {
								mappingNotes.add("Inactive concept");
							}
							loincPartToAttributeMap.put(partNum, new RelationshipTemplate(null, attributeValue));
						}

						if (!mappingNotes.isEmpty()) {
							partMapNotes.put(partNum, String.join("\n", mappingNotes));
							mappingNotes.clear();
						}
					}
				}
			}
			
			LOGGER.info("Populated map of " + loincPartToAttributeMap.size() + " LOINC parts to attributes");
			int tabIdx = ls.getTab(TAB_SUMMARY);
			ls.report(tabIdx, "");
			ls.report(tabIdx, "successfullTypeReplacement",successfullTypeReplacement);
			ls.report(tabIdx, "unsuccessfullTypeReplacement",unsuccessfullTypeReplacement);
			ls.report(tabIdx, "successfullValueReplacement",successfullValueReplacement);
			ls.report(tabIdx, "unsuccessfullValueReplacement",unsuccessfullValueReplacement);
			ls.report(tabIdx, "lexicallyMatchingMapReuse",lexicallyMatchingMapReuse);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to read " + attributeMapFile + " at line " + lineNum, e);
		}
	}

	public Concept replaceValueIfRequired(List<String> mappingNotes, Concept attributeValue, String partNum,
										  String partName, String partStatus) throws TermServerScriptException {
		
		
		if (!attributeValue.isActive()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementValue = knownReplacementMap.get(attributeValue);
			if (replacementValue == null) {
				hardCodedIndicator = "";
				replacementValue = ls.getReplacementSafely(mappingNotes, partNum, attributeValue, false);
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

	public Concept replaceTypeIfRequired(List<String> mappingNotes, Concept attributeType, String partNum) throws TermServerScriptException {
		if (hardCodedTypeReplacementMap.containsKey(attributeType)) {
			attributeType = hardCodedTypeReplacementMap.get(attributeType);
		}
		
		if (!attributeType.isActive()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementType = knownReplacementMap.get(attributeType);
			if (replacementType == null) {
				hardCodedIndicator = "";
				replacementType = ls.getReplacementSafely(mappingNotes, partNum, attributeType, false);
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
		return loincPartToAttributeMap.containsKey(loincPartNum);
	}

	public Set<String> getAllMappedLoincPartNums() {
		return loincPartToAttributeMap.keySet();
	}

	private void populateHardCodedMappings() throws TermServerScriptException {
		hardCodedMappings.put("LP29646-4", List.of(
				gl.getConcept("580171010000100 |Estimated average glucose calculation (calculation)|")));
		hardCodedMappings.put("LP443407-4", List.of(
				gl.getConcept("580191010000104 |Entitic mean volume (property) (qualifier value)|")));
		hardCodedMappings.put("LP443403-3", List.of(
				gl.getConcept("718499004 |Color (property) (qualifier value)|")));
		hardCodedMappings.put("LP443402-5", List.of(
				gl.getConcept("726564007 |Specific gravity (property) (qualifier value)|")));
		hardCodedMappings.put("LP443404-1", List.of(
				gl.getConcept("580181010000102 |Entitic mass concentration (property) (qualifier value)")));
		hardCodedMappings.put("LP30809-5", List.of(
				gl.getConcept("540101010000101 |Anion gap calculation (calculation)|")));
		hardCodedMappings.put("LP15429-1", List.of(
				gl.getConcept("570181010000107 |Base excess calculation (calculation)|")));
		hardCodedMappings.put("LP17102-2", List.of(
				gl.getConcept("570191010000105 |International normalized ratio calculation (calculation)|")));
		hardCodedMappings.put("LP32756-6", List.of(
				gl.getConcept("570161010000104 |Iron saturation calculation (calculation)|")));
		hardCodedMappings.put("LP21258-6", List.of(
				gl.getConcept("570171010000109 |Oxygen saturation calculation (calculation)|")));
		hardCodedMappings.put("LP38056-5", List.of(
				gl.getConcept("570091010000109 |Antigen of epidermal growth factor receptor (substance)|")));
		hardCodedMappings.put("LP65528-9", List.of(
				gl.getConcept("540161010000100 |Automated refractometry technique (qualifier value)|")));
		hardCodedMappings.put("LP15678-3", List.of(
				gl.getConcept("570151010000101 |Iron binding capacity calculation (calculation)|")));
		hardCodedMappings.put("LP443415-7", List.of(
				gl.getConcept("723206002 |Anion gap based on sodium, chloride and bicarbonate ions calculation technique (qualifier value)|")));
		hardCodedMappings.put("LP70259-4", List.of(
				gl.getConcept("570101010000100 |Automated technique (qualifier value)")));
		hardCodedMappings.put("LP443406-6", List.of(gl.getConcept("580111010000107 |Distribution width (property) (qualifier value)")));
		hardCodedMappings.put("LP36715-8", List.of(gl.getConcept("702668005 |Modification of diet in renal disease formula (qualifier value)")));
		hardCodedMappings.put("LP105134-3", List.of(gl.getConcept("703462006 |Cockcroft-Gault formula (qualifier value)|")));
		hardCodedMappings.put("LP71296-5", List.of(gl.getConcept("570111010000102 |Immature neutrophil (cell)|")));
		hardCodedMappings.put("LP69203-5", List.of(gl.getConcept("580101010000109 |Glomerular filtration rate (calculation)|")));
		hardCodedMappings.put("LP6879-3", List.of(gl.getConcept("762636008 |Duration (property) (qualifier value)|")));
		hardCodedMappings.put("LP21276-8", List.of(gl.getConcept("118576005 |Time ratio (property) (qualifier value)")));
		hardCodedMappings.put("LP443431-4", List.of(gl.getConcept("580121010000101 |Modification of diet in renal disease creatinine calculation formula relative to 1.73 square meters body surface area and adjusted for non-African race (qualifier value)|")));
		hardCodedMappings.put("LP443426-4", List.of(gl.getConcept("580131010000103 |Modification of diet in renal disease creatinine calculation formula relative to 1.73 square meters body surface area and adjusted for African race (qualifier value)|")));
		hardCodedMappings.put("LP443418-1", List.of(gl.getConcept("580141010000106 |Modification of diet in renal disease creatinine calculation formula relative to 1.73 square meters body surface area (qualifier value)|")));
		hardCodedMappings.put("LP443421-5", List.of(gl.getConcept("580151010000108 |Chronic Kidney Disease Epidemiology Collaboration creatinine calculation formula relative to 1.73 square meters body surface area (qualifier value)|")));
		hardCodedMappings.put("LP36683-8", List.of(gl.getConcept("106202009 |Antigen in ABO blood group system (substance)|"), gl.getConcept("16951006 |Antigen in Rh blood group system (substance)")));
		hardCodedMappings.put("LP15445-7", List.of(gl.getConcept("259498006 |Bilirubin glucuronide (substance)|"),
				gl.getConcept("73828001 |Bilirubin-albumin complex (substance)")));
		hardCodedMappings.put("LP182450-9", List.of(gl.getConcept("259337002 |Calcifediol (substance"),
				gl.getConcept("67517005 |25-hydroxyergocalciferol (substance)")));
		hardCodedMappings.put("LP443466-0", List.of(gl.getConcept("734842000 |Source (property) (qualifier value)|")));
		hardCodedMappings.put("LP443467-8", List.of(gl.getConcept("123038009 |Specimen (specimen)|")));
		hardCodedMappings.put("LP281728-8", List.of(gl.getConcept("410652009 |Blood product (product)|")));
	}

}
