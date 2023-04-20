package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

public class LoincTemplatedConceptWithInheres extends LoincTemplatedConcept {
	
	private LoincTemplatedConceptWithInheres(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithInheres templatedConcept = new LoincTemplatedConceptWithInheres(loincNum);
		templatedConcept.typeMap.put("PROPERTY", gl.getConcept("370130000 |Property (attribute)|"));
		templatedConcept.typeMap.put("SCALE", gl.getConcept("370132008 |Scale type (attribute)|"));
		templatedConcept.typeMap.put("TIME", gl.getConcept("370134009 |Time aspect (attribute)|"));
		templatedConcept.typeMap.put("SYSTEM", gl.getConcept("704327008  |Direct site (attribute)|"));
		templatedConcept.typeMap.put("METHOD", gl.getConcept("246501002 |Technique (attribute)|"));
		templatedConcept.typeMap.put("COMPONENT", gl.getConcept("704319004 |Inheres in (attribute)|"));
		
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept inheresIn = gl.getConcept("704319004 |Inheres in (attribute)|");
		if (CompNumPnIsSafe(loincNum)) {
			//Use COMPNUM_PN LOINC Part map to model Inheres in
			attributes.add(getAttributeFromDetailWithType(loincNum, LoincDetail.COMPNUM_PN, inheresIn));
		} else {
			LoincDetail denom = getLoincDetailIfPresent(loincNum, LoincDetail.COMPDENOM_PN);
			if (denom != null) {
				attributes.add(getAttributeFromDetailWithType(loincNum, LoincDetail.COMPNUM_PN, inheresIn));
				attributes.add(getAttributeFromDetail(loincNum, LoincDetail.COMPDENOM_PN));
			}
			
			if (detailPresent(loincNum, LoincDetail.COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					attributes.add(getAttributeFromDetailWithType(loincNum, LoincDetail.COMPNUM_PN, inheresIn));
				}
				attributes.add(getAttributeFromDetail(loincNum, LoincDetail.COMPSUBPART2_PN));
			}
		}
		return attributes;
	}
}
