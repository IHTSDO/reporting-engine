package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithSusceptibility extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithSusceptibility(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithSusceptibility templatedConcept = new LoincTemplatedConceptWithSusceptibility(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("COMPONENT", gl.getConcept("704320005 |Towards (attribute)|"));
		templatedConcept.setPreferredTermTemplate("[PROPERTY] to [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(boolean expectNullMap) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file

		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get("COMPONENT");

		Concept inheresIn = gl.getConcept("704319004 |Inheres in (attribute)|");
		Concept organism = gl.getConcept("410607006 |Organism (organism)|");
		//All Susceptibility concepts will have a Inheres in of Organism
		attributes.add(new RelationshipTemplate(inheresIn, organism));

		if (hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
		} else {
			processSubComponents(attributes, componentAttribType);
		}

		ensureComponentMappedOrRepresentedInTerm(attributes);
		return attributes;
	}
}
