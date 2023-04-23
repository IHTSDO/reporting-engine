package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

public class LoincTemplatedConceptWithComponent extends LoincTemplatedConcept {
	
	private LoincTemplatedConceptWithComponent(String loincNum) {
		super(loincNum);
	}
	
	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithComponent templatedConcept = new LoincTemplatedConceptWithComponent(loincNum);
		templatedConcept.typeMap.put("PROPERTY", gl.getConcept("370130000 |Property (attribute)|"));
		templatedConcept.typeMap.put("SCALE", gl.getConcept("370132008 |Scale type (attribute)|"));
		templatedConcept.typeMap.put("TIME", gl.getConcept("370134009 |Time aspect (attribute)|"));
		templatedConcept.typeMap.put("SYSTEM", gl.getConcept("704327008 |Direct site (attribute)|"));
		templatedConcept.typeMap.put("METHOD", gl.getConcept("246501002 |Technique (attribute)|"));
		templatedConcept.typeMap.put("COMPONENT", gl.getConcept("246093002 |Component (attribute)|"));
		templatedConcept.typeMap.put("DEVICE", gl.getConcept("424226004 |Using device (attribute)|"));
		templatedConcept.typeMap.put("PRECONDITION", precondition);
		
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [PRECONDITION]";
		return templatedConcept;
	}
	
	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		if (CompNumPnIsSafe(loincNum) && true) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetail(attributes,loincNum, LoincDetail.COMPNUM_PN, issues);
		} else {
			if (detailPresent(loincNum, LoincDetail.COMPNUM_PN)) {
				addAttributeFromDetail(attributes, loincNum, LoincDetail.COMPNUM_PN, issues);
				if (detailPresent(loincNum, LoincDetail.COMPDENOM_PN)) {
					addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPDENOM_PN, issues, relativeTo);
				}
			}
			
			if (detailPresent(loincNum, LoincDetail.COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					addAttributeFromDetail(attributes, loincNum, LoincDetail.COMPNUM_PN, issues);
				}
				if (detailPresent(loincNum, LoincDetail.COMPSUBPART2_PN)) {
					addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPSUBPART2_PN, issues, precondition);
				}
			}
			
			if (detailPresent(loincNum, LoincDetail.COMPSUBPART3_PN) ||
				detailPresent(loincNum, LoincDetail.COMPSUBPART4_PN)) {
				LoincDetail componentDetail = getLoincDetail(loincNum, LoincDetail.COMPONENT_PN);
				slotTermMap.put("COMPONENT", componentDetail.getPartName());
			}
		}
		return attributes;
	}

}
