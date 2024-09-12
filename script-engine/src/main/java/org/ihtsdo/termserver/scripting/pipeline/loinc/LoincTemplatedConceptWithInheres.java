package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
See Processing instruction document https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
 */
public class LoincTemplatedConceptWithInheres extends LoincTemplatedConcept {

	private static final List<String> BioPhageSuffixExceptions = List.of("LP438877-5", "LP438878-3", "LP134392-2");

	private static final String PROPERTY_ID_EXCEPTION = "LP6850-4"; //See instructions 2.f.ii.6.a.i.2
	private static final String TYPE_ID_EXCEPTION = "LP6886-8"; //See instructions 2.f.ii.6.a.i.1.a.i

	private LoincTemplatedConceptWithInheres(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithInheres templatedConcept = new LoincTemplatedConceptWithInheres(loincNum);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, gl.getConcept("704319004 |Inheres in (attribute)|"));
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get(LOINC_PART_TYPE_COMPONENT);
		
		if (!compNumPartNameAcceptable(attributes)) {
			return attributes;
		}
		if (hasNoSubParts()) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, getLoincDetailOrThrow(COMPNUM_PN), componentAttribType);
		} else {
			processSubComponents(attributes, componentAttribType);
		}

		if (detailPresent(COMPNUMSUFFIX_PN)) {
			LoincDetail componentDetail = getLoincDetailOrThrow(COMPNUMSUFFIX_PN);
			if (BioPhageSuffixExceptions.contains(componentDetail.getPartNumber())) {
				//use the COMPNUM_PN LP name to include in FSN and PT in the Inheres in slot for terming.
				String compNumPartName = getLoincDetailOrThrow(COMPNUM_PN).getPartName();
				slotTermMap.put(LOINC_PART_TYPE_COMPONENT, compNumPartName);
			}
		}

		//If we didn't find the component, return a null so that we record that failed mapping usage
		//And in fact, don't map this term at all
		if (attributes.isEmpty()) {
			attributes.add(null);
			if (!hasProcessingFlag(ProcessingFlag.ALLOW_BLANK_COMPONENT)) {
				processingFlags.add(ProcessingFlag.DROP_OUT);
			}
		}
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificRules(List<RelationshipTemplate> attributes, LoincDetail loincDetail, RelationshipTemplate rt) throws TermServerScriptException {
		//Rule 2.f.ii.6.a.i.2
		if (loincDetail.getPartNumber().equals(PROPERTY_ID_EXCEPTION)) {
			//Is our COMPNUM LP19429-7	Specimen source?
			LoincDetail compNum = getLoincDetailOrThrow(COMPNUM_PN);
			if (compNum.getPartNumber().equals("LP19429-7")) {
				rt.setTarget(gl.getConcept("734842000 |Source (property) (qualifier value)|"));
			}
		}

		//Rule 2.f.ii.6.a.i.1.a.i
		//When the property is 'type' and the suffix indicates a biophage, then the technique
		//will come from the suffix, rather than the method
		if (loincDetail.getPartTypeName().equals(LOINC_PART_TYPE_PROPERTY) &&
				getLoincDetailForPartType(LOINC_PART_TYPE_PROPERTY).getPartNumber().equals(TYPE_ID_EXCEPTION) &&
				hasDetail(COMPNUMSUFFIX_PN) &&
				BioPhageSuffixExceptions.contains(getLoincDetailOrThrow(COMPNUMSUFFIX_PN).getPartNumber())) {
			String partNum = getLoincDetailOrThrow(COMPNUMSUFFIX_PN).getPartNumber();
			List<RelationshipTemplate> additionalAttributes = cpm.getAttributePartManager().getPartMappedAttributeForType(NOT_SET, externalIdentifier, partNum, typeMap.get(LOINC_PART_TYPE_METHOD));
			attributes.addAll(additionalAttributes);
			processingFlags.add(ProcessingFlag.SUPPRESS_METHOD_TERM);
		}

		super.applyTemplateSpecificRules(attributes, loincDetail, rt);
	}

}
