package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincAttributePartMapManager extends AttributePartMapManager implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincAttributePartMapManager.class);

	private static final int IDX_PART_NUM = 0;
	private static final int IDX_STATUS = 7;
	private static final int IDX_NO_MAP = 6;
	private static final int IDX_TARGET = 2;

	private final LoincScript ls;
	
	public LoincAttributePartMapManager (LoincScript ls, Map<String, Part> partMap, Map<String, String> partMapNotes) {
		super(ls, partMap, partMapNotes);
		this.ls = ls;
	}

	protected void populateKnownMappings() throws TermServerScriptException {
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

	public void populatePartAttributeMap(File attributeMapFile) throws TermServerScriptException {
		// Output format from Snap2SNOMED is expected to be:
		// Source code[0]   Source display  Status  PartTypeName    Target code[4]  Target display  Relationship type code  Relationship type display   No map flag[8] Status[9]
		populateKnownMappings();
		int lineNum = 0;
		Set<String> partsSeen = new HashSet<>();
		List<String> mappingNotes = new ArrayList<>();

		try {
			LOGGER.info("Loading Part / Attribute Map Base File: {}", attributeMapFile);
			try (BufferedReader br = new BufferedReader(new FileReader(attributeMapFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (!line.isEmpty() && lineNum > 1) {
						processPartFileLine(line, partsSeen, mappingNotes);
					}
				}
			}
			
			LOGGER.info("Populated map of {} LOINC parts to attributes", partToAttributeMap.size());
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

	private void processPartFileLine(String line, Set<String> partsSeen, List<String> mappingNotes) throws TermServerScriptException {
		String[] items = line.split("\t");
		String partNum = items[IDX_PART_NUM];
		//Do we expect to see a map here?  Snap2Snomed also outputs unmapped parts
		if (items[IDX_STATUS].equals("UNMAPPED") || items[IDX_STATUS].equals("DRAFT")) {
			//Skip this one
		} else if (items[IDX_NO_MAP].equals("true")) {
			//And we can have items that report being mapped, but with 'no map' - warn about those.
			mappingNotes.add("Map indicates part mapped to 'No Map'");
		} else if (items[IDX_STATUS].equals("REJECTED")) {
			//And we can have items that report being mapped, but with 'no map' - warn about those.
			mappingNotes.add("Map indicates non-viable map - " + items[IDX_STATUS]);
		} else if (partsSeen.contains(partNum)) {
			//Have we seen this part before?  Map should now be unique
			mappingNotes.add("Part / Attribute BaseFile contains duplicate entry for " + partNum);
		} else {
			partsSeen.add(partNum);
			Concept attributeValue = gl.getConcept(items[IDX_TARGET], false, true);
			attributeValue = replaceValueIfRequired(mappingNotes, attributeValue);
			if (attributeValue != null && attributeValue.isActive()) {
				mappingNotes.add("Inactive concept");
			}
			partToAttributeMap.put(partNum, new RelationshipTemplate(null, attributeValue));
		}

		if (!mappingNotes.isEmpty()) {
			partMapNotes.put(partNum, String.join("\n", mappingNotes));
			mappingNotes.clear();
		}
		
	}

	@Override
	public Concept replaceValueIfRequired(List<String> mappingNotes, Concept attributeValue) {

		if (!attributeValue.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementValue = knownReplacementMap.get(attributeValue);
			if (replacementValue == null) {
				hardCodedIndicator = "";
				replacementValue = ls.getReplacementSafely(mappingNotes, attributeValue, false);
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

	@Override
	public Concept replaceTypeIfRequired(List<String> mappingNotes, Concept attributeType) {
		if (hardCodedTypeReplacementMap.containsKey(attributeType)) {
			attributeType = hardCodedTypeReplacementMap.get(attributeType);
		}
		
		if (!attributeType.isActiveSafely()) {
			String hardCodedIndicator = " hardcoded";
			Concept replacementType = knownReplacementMap.get(attributeType);
			if (replacementType == null) {
				hardCodedIndicator = "";
				replacementType = ls.getReplacementSafely(mappingNotes, attributeType, false);
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

	@Override
	public boolean containsMappingForLoincPartNum(String loincPartNum) {
		return partToAttributeMap.containsKey(loincPartNum);
	}

	@Override
	public Set<String> getAllMappedLoincPartNums() {
		return partToAttributeMap.keySet();
	}

	protected void populateHardCodedMappings() throws TermServerScriptException {
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
		hardCodedMappings.put("LP105134-3", List.of(gl.getConcept("703462006 |Cockcroft-Gault formula (qualifier value)|")));
		hardCodedMappings.put("LP71296-5", List.of(gl.getConcept("570111010000102 |Immature neutrophil (cell)|")));
		hardCodedMappings.put("LP69203-5", List.of(gl.getConcept("580101010000109 |Glomerular filtration rate (calculation)|")));
		hardCodedMappings.put("LP6879-3", List.of(gl.getConcept("762636008 |Duration (property) (qualifier value)|")));
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
		hardCodedMappings.put("LP63085-2", List.of(gl.getConcept("580201010000101 |Creatinine renal clearance calculation (calculation)|")));
		hardCodedMappings.put("LP444405-7", List.of(gl.getConcept("580161010000105 |Sedimentation process (qualifier value)|")));
		hardCodedMappings.put("LP443823-2", List.of(gl.getConcept("540151010000102 |Disposition (property) (qualifier value)|")));
		hardCodedMappings.put("LP444407-3", List.of(gl.getConcept("645121010000107 |Relative velocity (property) (qualifier value)|")));
		hardCodedMappings.put("LP65367-2", List.of(gl.getConcept("645131010000105 |Nucleated blood cell (cell)|")));
		hardCodedMappings.put("LP31542-1", List.of(gl.getConcept("655141010000100 |Immature basophil (cell)|")));
		hardCodedMappings.put("LP31543-9", List.of(gl.getConcept("655131010000108 |Immature eosinophil (cell)|")));
		hardCodedMappings.put("LP31551-2", List.of(gl.getConcept("655151010000103 |Immature monocyte (cell)|")));
		hardCodedMappings.put("LP446464-2", List.of(gl.getConcept("87612001 |Blood (substance)|")));
		hardCodedMappings.put("LP446469-1", List.of(gl.getConcept("32457005 |Body fluid (substance)|")));
		hardCodedMappings.put("LP446467-5", List.of(gl.getConcept("4635002 |Arterial blood (substance)|")));
		hardCodedMappings.put("LP446471-7", List.of(gl.getConcept("53130003 |Venous blood (substance)|")));
	}

}
