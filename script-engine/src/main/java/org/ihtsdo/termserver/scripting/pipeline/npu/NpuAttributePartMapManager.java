package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NpuAttributePartMapManager extends AttributePartMapManager {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NpuAttributePartMapManager.class);

	protected NpuAttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> parts,
			Map<String, String> partMapNotes) {
		super(cpm, parts, partMapNotes);
	}

	@Override
	protected void populateKnownMappings() throws TermServerScriptException {
		LOGGER.warn("NPU has no mapping overrides");
	}

	@Override
	protected void populateHardCodedMappings() throws TermServerScriptException {
		hardCodedMappings.put("Ratio", List.of(
				gl.getConcept("30766002|Quantitative (qualifier value)|")));
		hardCodedMappings.put("Ordinal", List.of(
				gl.getConcept("117363000 |Ordinal value|")));
		hardCodedMappings.put("Nominal", List.of(
				gl.getConcept("117362005 |Nominal value|")));
		hardCodedMappings.put("UMLSC0544483", List.of(
				gl.getConcept("259698001|Globin chain (substance)|")));
		hardCodedMappings.put("QU60166", List.of(
				gl.getConcept("59570008|Transferrin (substance)|")));
		hardCodedMappings.put("QU80078", List.of(
				gl.getConcept("67517005|25-hydroxyergocalciferol (substance)|"),
				gl.getConcept("259337002|Calcifediol (substance)|")));
		hardCodedMappings.put("QU61179", List.of(
				gl.getConcept("703491009|Tissue transglutaminase immunoglobulin A antibody (substance)|")));
		hardCodedMappings.put("QU80065", List.of(
				gl.getConcept("26821000|Apolipoprotein B (substance)|"),
				gl.getConcept("102720003|Apolipoprotein A-I (substance)|")));
		hardCodedMappings.put("QU80262", List.of(
				gl.getConcept("733830002|Glycated hemoglobin-A1c (substance)|")));
		hardCodedMappings.put("QU100796", List.of(
				gl.getConcept("1174032008|Immunoglobulin G antibody to deamidated gliadin peptide (substance)|")));
		hardCodedMappings.put("QU50106", List.of(
				gl.getConcept("118569000|Arbitrary concentration (property) (qualifier value)|")));
		hardCodedMappings.put("QU50063", List.of(
				gl.getConcept("410656007|Type (property) (qualifier value)|")));
		hardCodedMappings.put("QU50032", List.of(
				gl.getConcept("118523000|Catalytic activity (property) (qualifier value)|")));
		hardCodedMappings.put("QU50110", List.of(
				gl.getConcept("118531005|Arbitrary entitic (property) (qualifier value)|")));
		hardCodedMappings.put("QU71310", List.of(
				gl.getConcept("40885006|Variant (qualifier value)|")));
		hardCodedMappings.put("QU50346", List.of(
				gl.getConcept("1348323004|10^9/liter (qualifier value)|")));
		hardCodedMappings.put("QU50345", List.of(
				gl.getConcept("1348325006|10^12/liter (qualifier value)|")));
		hardCodedMappings.put("QU09442", List.of(
				gl.getConcept("258796002|Milligram/liter (qualifier value)|")));
		hardCodedMappings.put("QU09411", List.of(
				gl.getConcept("427323004|Micromole/millimole (qualifier value)|")));
		hardCodedMappings.put("QU63075", List.of(
				gl.getConcept("103184005|Antithyroperoxidase antibody (substance)|")));
		hardCodedMappings.put("QU62247", List.of(
				gl.getConcept("388296008|Cat dander specific immunoglobulin E (substance)|")));
		hardCodedMappings.put("QU101474", List.of(
				gl.getConcept("725730001|Phosphatidylethanol (substance)|")));
		hardCodedMappings.put("QU100349", List.of(
				gl.getConcept("260073007|Human leukocyte antigen B allele (substance)|")));
		hardCodedMappings.put("Hugo4944", List.of(
				gl.getConcept("260082001|Human leukocyte antigen DQB1 allele (substance)|")));
	}

}
