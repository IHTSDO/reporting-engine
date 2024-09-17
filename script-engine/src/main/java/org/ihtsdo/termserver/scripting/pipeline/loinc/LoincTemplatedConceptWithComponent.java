package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

public class LoincTemplatedConceptWithComponent extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithComponent(ExternalConcept externalConcept) {
		super(externalConcept);
	}
	
	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithComponent templatedConcept = new LoincTemplatedConceptWithComponent(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}
	
	@Override
	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get("COMPONENT");

		if (!compNumPartNameAcceptable(attributes)) {
			return attributes;
		}
		
		if (hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
		} else {
			if (detailPresent(COMPNUM_PN)) {
				addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
				if (detailPresent(COMPDENOM_PN)) {
					addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPDENOM_PN), relativeTo);
				}
			}
			processSubComponents(attributes, componentAttribType);
		}

		//If we didn't find the component, return a null so that we record that failed mapping usage
		if (attributes.isEmpty()) {
			attributes.add(null);
		}
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, LoincDetail loincDetail, RelationshipTemplate rt) throws TermServerScriptException {
		//Temporary rule.  If our target is Influenza, replace that with Influenza A, B & C
		Concept influenzaAb = gl.getConcept("259856001 |Influenza antibody (substance)|");
		if (rt.getTarget().equals(influenzaAb)) {
			attributes.clear();
			List<Concept> newAntibodies = List.of(
					gl.getConcept("120753009 |Antibody to Influenza A virus (substance)|"),
					gl.getConcept("120843002 |Antibody to Influenza B virus (substance)"),
					gl.getConcept("120844008 |Antibody to Influenza C virus (substance)|"));
			newAntibodies.forEach(a -> {
				RelationshipTemplate newRt = rt.clone();
				newRt.setTarget(a);
				attributes.add(newRt);
			});
			slotTermMap.put(LOINC_PART_TYPE_COMPONENT, "influenza antibody");
		}

		super.applyTemplateSpecificModellingRules(attributes, loincDetail, rt);
	}

}
