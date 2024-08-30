package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithRatio extends LoincTemplatedConcept {

	private static final String SEPARATOR = "#SEPARATOR#";

	private LoincTemplatedConceptWithRatio(String loincNum) {
		super(loincNum);
	}

	@Override
	protected Concept getParentConceptForTemplate() throws TermServerScriptException {
		return gl.getConcept("540131010000107 |Ratio observable (observable entity)|");
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithRatio templatedConcept = new LoincTemplatedConceptWithRatio(loincNum);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		//The 'to' changes to a slash in the PT
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT]" + SEPARATOR + "[DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttrib = typeMap.get(LOINC_PART_TYPE_COMPONENT);
		Concept challengeAttrib = typeMap.get(LOINC_PART_TYPE_CHALLENGE);
		if (hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetail(COMPNUM_PN), componentAttrib);
		} else {
			LoincDetail denom = getLoincDetail(COMPDENOM_PN);
			if (denom != null) {
				addAttributeFromDetailWithType(attributes, getLoincDetail(COMPNUM_PN), componentAttrib);
				addAttributeFromDetailWithType(attributes, getLoincDetail(COMPDENOM_PN), relativeTo);
				//Check for percentage
				if (denom.getPartName().contains("100")) {
					attributes.add(percentAttribute);
					slotTermMap.put("PROPERTY", "percentage");
				}
			}

			if (detailPresent(COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					addAttributeFromDetailWithType(attributes, getLoincDetail(COMPNUM_PN), componentAttrib);
				}
				addAttributeFromDetailWithType(attributes, getLoincDetail(COMPSUBPART2_PN), challengeAttrib);
			}
		}

		//If we didn't find the component, return a null so that we record that failed mapping usage
		if (attributes.isEmpty()) {
			attributes.add(null);
		}
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificRules(Description d) {
		//If this is the FSN we separate with 'to'.
		//For the PT, speparate with '/'
		if (d.getType().equals(DescriptionType.FSN)) {
			d.setTerm(d.getTerm().replace(SEPARATOR, " to "));
		} else {
			d.setTerm(d.getTerm().replace(SEPARATOR, "/"));
		}
	}
}
