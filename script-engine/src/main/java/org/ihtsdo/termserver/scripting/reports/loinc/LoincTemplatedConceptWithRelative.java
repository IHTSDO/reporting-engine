package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

public class LoincTemplatedConceptWithRelative extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithRelative(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithRelative templatedConcept = new LoincTemplatedConceptWithRelative(loincNum);
		templatedConcept.typeMap.put("PROPERTY", gl.getConcept("370130000 |Property (attribute)|"));
		templatedConcept.typeMap.put("SCALE", gl.getConcept("370132008 |Scale type (attribute)|"));
		templatedConcept.typeMap.put("TIME", gl.getConcept("370134009 |Time aspect (attribute)|"));
		templatedConcept.typeMap.put("SYSTEM", gl.getConcept("704327008  |Direct site (attribute)|"));
		templatedConcept.typeMap.put("METHOD", gl.getConcept("246501002 |Technique (attribute)|"));
		templatedConcept.typeMap.put("COMPONENT", gl.getConcept("246093002 |Component (attribute)|"));
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		templatedConcept.typeMap.put("UNITS", gl.getConcept("415067009 |Percentage unit (qualifier value)|"));
		templatedConcept.typeMap.put("DEVICE", gl.getConcept("424226004 |Using device (attribute)|"));
		templatedConcept.typeMap.put("CHALLENGE", precondition);
		
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] to [DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttrib = typeMap.get("COMPONENT");
		Concept challengeAttrib = typeMap.get("CHALLENGE"); 
		if (CompNumPnIsSafe(loincNum)) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes,loincNum, LoincDetail.COMPNUM_PN, issues, componentAttrib);
		} else {
			LoincDetail denom = getLoincDetailIfPresent(loincNum, LoincDetail.COMPDENOM_PN);
			if (denom != null) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, componentAttrib);
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPDENOM_PN, issues, relativeTo);
				//Check for percentage
				if (denom.getPartName().contains("100")) {
					attributes.add(percentAttribute);
					slotTermMap.put("PROPERTY", "percentage");
				}
			}
			
			if (detailPresent(loincNum, LoincDetail.COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, componentAttrib);
				}
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPSUBPART2_PN, issues, challengeAttrib);
			}
		}
		
		//If we didn't find the component, return a null so that we record that failed mapping usage
		if (attributes.size() == 0) {
			attributes.add(null);
		}
		return attributes;
	}


}
