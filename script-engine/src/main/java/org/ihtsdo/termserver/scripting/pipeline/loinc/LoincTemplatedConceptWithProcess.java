package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithProcess extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithProcess(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	private static Concept PROCESS_OUTPUT;

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithProcess templatedConcept = new LoincTemplatedConceptWithProcess(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		if (PROCESS_OUTPUT == null) {
			PROCESS_OUTPUT = gl.getConcept("704324001 |Process output (attribute)|");
		}
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, PROCESS_OUTPUT);

		//See https://confluence.ihtsdotools.org/display/SCTEMPLATES/Process+Observable+for+LOINC+%28observable+entity%29+-+v1.0
		//[property] of [characterizes] of [process output] in [process duration] in [direct site] by [technique] using [using device] [precondition] (observable entity)
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [CHARACTERIZES] of [COMPONENT] in [TIME] in [SYSTEM] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file

		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get(LOINC_PART_TYPE_COMPONENT);

		if (hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
		} else {
			processSubComponents(attributes, componentAttribType);
		}

		ensureComponentMappedOrRepresentedInTerm(attributes);
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, LoincDetail loincDetail, RelationshipTemplate rt) throws TermServerScriptException {
		//Rule v.3.4.a & b
		//All process observables will have an agent and characterizes.
		//But only if we're working with Urine
		if (loincDetail.getLDTColumnName().equals(SYSTEM_PN)
			&& loincDetail.getPartName().contains("Urine")) {
			Concept agent = gl.getConcept("704322002 |Process agent (attribute)|");
			Concept kidneyStruct = gl.getConcept("64033007 |Kidney structure (body structure)|");
			attributes.add(new RelationshipTemplate(agent, kidneyStruct));

			Concept characterizes = gl.getConcept("704321009 |Characterizes (attribute)|");
			Concept excrtProc = gl.getConcept("718500008 |Excretory process (qualifier value)| ");
			attributes.add(new RelationshipTemplate(characterizes, excrtProc));
			slotTermMap.put("CHARACTERIZES", "excretion");
		} else {
			addProcessingFlag(ProcessingFlag.SUPPRESS_CHARACTERIZES_TERM);
		}

		//Rule vi.6.b   LP16409-2 Erythrocyte sedimentation rate
		if (loincDetail.getLDTColumnName().equals(COMPNUM_PN) && loincDetail.getPartNumber().equals("LP16409-2")) {
			swapAttributeType(attributes, PROCESS_OUTPUT, gl.getConcept("704321009 |Characterizes (attribute)|"));
			RelationshipTemplate additionalAttribute = new RelationshipTemplate(
					gl.getConcept("1003735000 |Process acts on (attribute)|"),
					gl.getConcept("418525009 |Erythrocyte component of blood (substance)|"));
			attributes.add(additionalAttribute);
		}

		super.applyTemplateSpecificModellingRules(attributes, loincDetail, rt);
	}

	protected void applyTemplateSpecificTermingRules(Description d) {
		//If the CHARACTERIZES slot is still here but we're allowing it to be empty,
		//remove it, and its connector
		if (hasProcessingFlag(ProcessingFlag.SUPPRESS_CHARACTERIZES_TERM)
				&& d.getTerm().contains("[CHARACTERIZES]")) {
			d.setTerm(d.getTerm().replace(" of [CHARACTERIZES]", ""));
		}
	}

	private void swapAttributeType(List<RelationshipTemplate> attributes, Concept find, Concept replace) {
		for (RelationshipTemplate rt : attributes) {
			if (rt.getType().equals(find)) {
				rt.setType(replace);
			}
		}
	}
}
