package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithProcess extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithProcess(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithProcess templatedConcept = new LoincTemplatedConceptWithProcess(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, gl.getConcept("704320005 |Towards (attribute)|"));

		//See https://confluence.ihtsdotools.org/display/SCTEMPLATES/Process+Observable+for+LOINC+%28observable+entity%29+-+v1.0
		//[property] of [characterizes] of [process output] in [process duration] in [direct site] by [technique] using [using device] [precondition] (observable entity)
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of excretion of [COMPONENT] in [TIME] in [SYSTEM] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file

		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get(LOINC_PART_TYPE_COMPONENT);

		if (!compNumPartNameAcceptable(attributes)) {
			return attributes;
		}
		if (hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
		} else {
			processSubComponents(attributes, componentAttribType);
		}

		//If we didn't find the component, return a null so that we record that failed mapping usage
		//And in fact, don't map this term at all
		if (attributes.isEmpty()) {
			attributes.add(null);
			if (!hasProcessingFlag(ProcessingFlag.ALLOW_BLANK_COMPONENT)) {
				addProcessingFlag(ProcessingFlag.DROP_OUT);
			}
		}
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificRules(List<RelationshipTemplate> attributes, LoincDetail loincDetail, RelationshipTemplate rt) throws TermServerScriptException {
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
		}

		super.applyTemplateSpecificRules(attributes, loincDetail, rt);
	}
}
