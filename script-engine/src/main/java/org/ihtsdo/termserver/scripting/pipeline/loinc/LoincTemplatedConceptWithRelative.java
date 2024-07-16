package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincTemplatedConceptWithRelative extends LoincTemplatedConcept {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConceptWithRelative.class);

	private LoincTemplatedConceptWithRelative(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithRelative templatedConcept = new LoincTemplatedConceptWithRelative(loincNum);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		templatedConcept.typeMap.put("UNITS", gl.getConcept("415067009 |Percentage unit (qualifier value)|"));
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] to [DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttrib = typeMap.get(LOINC_PART_TYPE_COMPONENT);
		Concept challengeAttrib = typeMap.get(LOINC_PART_TYPE_CHALLENGE);
		if (CompNumPnIsSafe(loincNum)) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttrib);
		} else {
			LoincDetail denom = getLoincDetailIfPresent(loincNum, COMPDENOM_PN);
			if (denom != null) {
				addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttrib);
				addAttributeFromDetailWithType(attributes, COMPDENOM_PN, issues, relativeTo);
				//Check for percentage
				if (denom.getPartName().contains("100")) {
					attributes.add(percentAttribute);
					slotTermMap.put("PROPERTY", "percentage");
				}
			}

			if (detailPresent(loincNum, COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttrib);
				}
				addAttributeFromDetailWithType(attributes, COMPSUBPART2_PN, issues, challengeAttrib);
			}
		}

		//If we didn't find the component, return a null so that we record that failed mapping usage
		if (attributes.isEmpty()) {
			attributes.add(null);
		}
		return attributes;
	}
}
