package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
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
		templatedConcept.typeMap.put("DEVICE", gl.getConcept("424226004 |Using device (attribute)|"));
		templatedConcept.typeMap.put("CHALLENGE", precondition);
		
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept inheresIn = gl.getConcept("704319004 |Inheres in (attribute)|");
		if (CompNumPnIsSafe(loincNum)) {
			//Use COMPNUM_PN LOINC Part map to model Inheres in
			if (!detailPresent(loincNum, LoincDetail.COMPNUM_PN)) {
				TermServerScript.debug("Check here");
			}
			//If we can't map the COMPNUM_PN then concept will be set to primitive.   Use an inhere from the core
			if (!addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, inheresIn)) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPONENTCORE_PN, issues, inheresIn);
			}
		} else {
			LoincDetail denom = getLoincDetailIfPresent(loincNum, LoincDetail.COMPDENOM_PN);
			if (denom != null) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, inheresIn);
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPDENOM_PN, issues, relativeTo);
			}
			
			if (detailPresent(loincNum, LoincDetail.COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, inheresIn);
				}
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPSUBPART2_PN, issues, precondition);
			}
			
			if (attributes.isEmpty() && detailPresent(loincNum, LoincDetail.COMPSUBPART3_PN)) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, inheresIn);
			}
			
			if (attributes.isEmpty() && detailPresent(loincNum, LoincDetail.COMPSUBPART4_PN)) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, inheresIn);
			}
		}
		
		if (detailPresent(loincNum, LoincDetail.COMPSUBPART3_PN) ||
				detailPresent(loincNum, LoincDetail.COMPSUBPART4_PN)) {
				LoincDetail componentDetail = getLoincDetail(loincNum, LoincDetail.COMPONENT_PN);
			slotTermMap.put("COMPONENT", componentDetail.getPartName());
		}
		
		//If we didn't find the component, return a null so that we record that failed mapping usage
		if (attributes.size() == 0) {
			attributes.add(null);
		}
		return attributes;
	}
}
