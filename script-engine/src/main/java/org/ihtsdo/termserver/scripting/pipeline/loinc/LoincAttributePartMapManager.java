package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;

public class LoincAttributePartMapManager extends AttributePartMapManager implements LoincScriptConstants {

	public LoincAttributePartMapManager (LoincScript ls, Map<String, Part> partMap, Map<String, String> partMapNotes) {
		super(ls, partMap, partMapNotes);
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

	@Override
	public boolean containsMappingForLoincPartNum(String loincPartNum) {
		return partToAttributeMap.containsKey(loincPartNum);
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
		hardCodedMappings.put("LP443406-6", List.of(
				gl.getConcept("580111010000107 |Distribution width (property) (qualifier value)")));
		hardCodedMappings.put("LP105134-3", List.of(
				gl.getConcept("703462006 |Cockcroft-Gault formula (qualifier value)|")));
		hardCodedMappings.put("LP71296-5", List.of(
				gl.getConcept("570111010000102 |Immature neutrophil (cell)|")));
		hardCodedMappings.put("LP69203-5", List.of(
				gl.getConcept("580101010000109 |Glomerular filtration rate (calculation)|")));
		hardCodedMappings.put("LP6879-3", List.of(
				gl.getConcept("762636008 |Duration (property) (qualifier value)|")));
		hardCodedMappings.put("LP443431-4", List.of(
				gl.getConcept("580121010000101 |Modification of diet in renal disease creatinine calculation formula relative to 1.73 square meters body surface area and adjusted for non-African race (qualifier value)|")));
		hardCodedMappings.put("LP443426-4", List.of(
				gl.getConcept("580131010000103 |Modification of diet in renal disease creatinine calculation formula relative to 1.73 square meters body surface area and adjusted for African race (qualifier value)|")));
		hardCodedMappings.put("LP443418-1", List.of(
				gl.getConcept("580141010000106 |Modification of diet in renal disease creatinine calculation formula relative to 1.73 square meters body surface area (qualifier value)|")));
		hardCodedMappings.put("LP443421-5", List.of(
				gl.getConcept("580151010000108 |Chronic Kidney Disease Epidemiology Collaboration creatinine calculation formula relative to 1.73 square meters body surface area (qualifier value)|")));
		hardCodedMappings.put("LP36683-8", List.of(
				gl.getConcept("106202009 |Antigen in ABO blood group system (substance)|"),
				gl.getConcept("16951006 |Antigen in Rh blood group system (substance)")));
		hardCodedMappings.put("LP15445-7", List.of(
				gl.getConcept("259498006 |Bilirubin glucuronide (substance)|"),
				gl.getConcept("73828001 |Bilirubin-albumin complex (substance)")));
		hardCodedMappings.put("LP182450-9", List.of(
				gl.getConcept("259337002 |Calcifediol (substance"),
				gl.getConcept("67517005 |25-hydroxyergocalciferol (substance)")));
		hardCodedMappings.put("LP443466-0", List.of(
				gl.getConcept("734842000 |Source (property) (qualifier value)|")));
		hardCodedMappings.put("LP443467-8", List.of(
				gl.getConcept("123038009 |Specimen (specimen)|")));
		hardCodedMappings.put("LP281728-8", List.of(
				gl.getConcept("410652009 |Blood product (product)|")));
		hardCodedMappings.put("LP63085-2", List.of(
				gl.getConcept("580201010000101 |Creatinine renal clearance calculation (calculation)|")));
		hardCodedMappings.put("LP444405-7", List.of(
				gl.getConcept("580161010000105 |Sedimentation process (qualifier value)|")));
		hardCodedMappings.put("LP443823-2", List.of(
				gl.getConcept("540151010000102 |Disposition (property) (qualifier value)|")));
		hardCodedMappings.put("LP444407-3", List.of(
				gl.getConcept("645121010000107 |Relative velocity (property) (qualifier value)|")));
		hardCodedMappings.put("LP65367-2", List.of(
				gl.getConcept("645131010000105 |Nucleated blood cell (cell)|")));
		hardCodedMappings.put("LP31542-1", List.of(
				gl.getConcept("655141010000100 |Immature basophil (cell)|")));
		hardCodedMappings.put("LP31543-9", List.of(
				gl.getConcept("655131010000108 |Immature eosinophil (cell)|")));
		hardCodedMappings.put("LP31551-2", List.of(
				gl.getConcept("655151010000103 |Immature monocyte (cell)|")));
		hardCodedMappings.put("LP446464-2", List.of(
				gl.getConcept("87612001 |Blood (substance)|")));
		hardCodedMappings.put("LP446469-1", List.of(
				gl.getConcept("32457005 |Body fluid (substance)|")));
		hardCodedMappings.put("LP446467-5", List.of(
				gl.getConcept("4635002 |Arterial blood (substance)|")));
		hardCodedMappings.put("LP446471-7", List.of(
				gl.getConcept("53130003 |Venous blood (substance)|")));
	}

}
