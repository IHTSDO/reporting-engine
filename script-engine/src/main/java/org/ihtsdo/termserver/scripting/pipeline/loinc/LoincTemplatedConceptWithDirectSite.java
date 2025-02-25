package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithDirectSite extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithDirectSite(ExternalConcept externalConcept) {
		super(externalConcept);
	}
	
	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithDirectSite templatedConcept = new LoincTemplatedConceptWithDirectSite(externalConcept);
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

		ensureComponentMappedOrRepresentedInTerm(attributes);
		return attributes;
	}

}
