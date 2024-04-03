package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincTemplatedConceptWithInheres extends LoincTemplatedConcept {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConceptWithInheres.class);

	private static List<String> suffixExceptions = List.of("LP438877-5", "LP438878-3", "LP134392-2");

	private LoincTemplatedConceptWithInheres(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithInheres templatedConcept = new LoincTemplatedConceptWithInheres(loincNum);
		templatedConcept.typeMap.put("PROPERTY", gl.getConcept("370130000 |Property (attribute)|"));
		templatedConcept.typeMap.put("SCALE", gl.getConcept("370132008 |Scale type (attribute)|"));
		templatedConcept.typeMap.put("TIME", gl.getConcept("370134009 |Time aspect (attribute)|"));
		templatedConcept.typeMap.put("SYSTEM", gl.getConcept("704327008 |Direct site (attribute)|"));
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
		Concept componentAttribType = typeMap.get("COMPONENT");
		
		//We can't yet deal with "given"
		if (detailPresent(loincNum, LoincDetail.COMPNUM_PN) &&
			getLoincDetail(loincNum, LoincDetail.COMPNUM_PN).getPartName().endsWith(" given")) {
			String issue = "Skipping concept using 'given'";
			cpm.report(getTab(TAB_IOI), issue, loincNum);
			issues.add(issue);
			attributes.add(null);
			return attributes;
		}

		if (CompNumPnIsSafe(loincNum)) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes,loincNum, LoincDetail.COMPNUM_PN, issues, componentAttribType);
		} else {
			if (detailPresent(loincNum, LoincDetail.COMPSUBPART2_PN)) {
				if(attributes.isEmpty()) {
					addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, componentAttribType);
				}
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPSUBPART2_PN, issues, precondition);
			}
			
			if (attributes.isEmpty() && detailPresent(loincNum, LoincDetail.COMPSUBPART3_PN)) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, componentAttribType);
			}
			
			if (attributes.isEmpty() && detailPresent(loincNum, LoincDetail.COMPSUBPART4_PN)) {
				addAttributeFromDetailWithType(attributes, loincNum, LoincDetail.COMPNUM_PN, issues, componentAttribType);
			}
		}

		if (detailPresent(loincNum, LoincDetail.COMPNUMSUFFIX_PN)) {
			LoincDetail componentDetail = getLoincDetail(loincNum, LoincDetail.COMPNUMSUFFIX_PN);
			if (suffixExceptions.contains(componentDetail.getPartNumber())) {
				//use the COMPNUM_PN LP name to include in FSN and PT in the Inheres in slot for terming.
				String compNumPartName = getLoincDetail(loincNum, LoincDetail.COMPNUM_PN).getPartName();
				slotTermMap.put("COMPONENT", compNumPartName);
			}
		}
		
		if (detailPresent(loincNum, LoincDetail.COMPSUBPART3_PN)) {
			LoincDetail componentDetail = getLoincDetail(loincNum, LoincDetail.COMPSUBPART3_PN);
			slotTermMap.put("COMPONENT", componentDetail.getPartName());
			processingFlags.add(ProcessingFlag.MARK_AS_PRIMITIVE);
		}
		
		if (detailPresent(loincNum, LoincDetail.COMPSUBPART4_PN)) {
			LoincDetail componentDetail = getLoincDetail(loincNum, LoincDetail.COMPSUBPART4_PN);
			slotTermMap.put("COMPONENT", componentDetail.getPartName());
			processingFlags.add(ProcessingFlag.MARK_AS_PRIMITIVE);
		}
		
		//If we didn't find the component, return a null so that we record that failed mapping usage
		//And in fact, don't map this term at all
		if (attributes.size() == 0) {
			attributes.add(null);
			processingFlags.add(ProcessingFlag.DROP_OUT);
		}
		return attributes;
	}

	protected RelationshipTemplate applyTemplateSpecificRules(String loincPartNum, RelationshipTemplate rt) throws TermServerScriptException {
		return super.applyTemplateSpecificRules(loincPartNum, rt);
	}
}
