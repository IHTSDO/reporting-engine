package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;

import static org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript.LOINC_PART_OBSERVATION;
import static org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript.LOINC_PART_SPECIMEN_VOLUME;

/*
See Processing instruction document https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
 */
public class LoincTemplatedConceptWithInheres extends LoincTemplatedConcept {

	private static final List<String> BioPhageSuffixExceptions = List.of("LP438877-5", "LP438878-3", "LP134392-2");

	private static final String PROPERTY_ID_EXCEPTION = "LP6850-4"; //See instructions 2.f.ii.6.a.i.2
	private static final String TYPE_ID_EXCEPTION = "LP6886-8"; //See instructions 2.f.ii.6.a.i.1.a.i

	protected LoincTemplatedConceptWithInheres(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithInheres templatedConcept = new LoincTemplatedConceptWithInheres(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, gl.getConcept("704319004 |Inheres in (attribute)|"));
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(boolean expectNullMap) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file
		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get(LOINC_PART_TYPE_COMPONENT);
		
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

		//Rule 9 for Prid Observations
		if (getExternalConcept().getProperty().equals("Prid")
				&& detailPresent(COMPNUM_PN)
				&& getLoincDetailOrThrow(COMPNUM_PN).getPartNumber().equals(LOINC_PART_OBSERVATION)) {
			if (this instanceof LoincTemplatedConceptWithInheresNoComponent) {
				slotTermMap.put(LOINC_PART_TYPE_PROPERTY, "Microscopic observation of finding");
				setPreferredTermTemplate("[PROPERTY] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
			} else {
				slotTermMap.put(LOINC_PART_TYPE_PROPERTY, "Microscopic observation");
				slotTermMap.put(LOINC_PART_TYPE_COMPONENT, "finding");
			}
		}

		if (!expectNullMap) {
			ensureComponentMappedOrRepresentedInTerm(attributes);
		}
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		LoincDetail loincDetail = (LoincDetail) part;
		//Rule 2.f.ii.6.a.i.2
		if (loincDetail.getPartNumber().equals(PROPERTY_ID_EXCEPTION)) {
			//Is our COMPNUM LP19429-7	Specimen source?
			LoincDetail compNum = getLoincDetailOrThrow(COMPNUM_PN);
			if (compNum.getPartNumber().equals("LP19429-7")) {
				rt.setTarget(gl.getConcept("734842000 |Source (property) (qualifier value)|"));
			}
		}

		if (loincDetail.getPartNumber().equals(LOINC_PART_SPECIMEN_VOLUME)) {
			//For an Inheres template, "specimen" will appear in the COMPONENT slot.
			addProcessingFlag(ProcessingFlag.ALLOW_SPECIMEN);
			setPreferredTermTemplate("[PROPERTY] of [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
			typeMap.remove(LOINC_PART_TYPE_COMPONENT);
			typeMap.put(LOINC_PART_TYPE_SYSTEM, gl.getConcept("704319004 |Inheres in (attribute)|"));
		}

		//If the component is Specimen Volume, then change the System from Direct Site to Inheres In
		if (rt != null && rt.getType().equals(DIRECT_SITE)) {
			LoincDetail component = getLoincDetailForColNameIfPresent(COMPNUM_PN);
			if (component != null && component.getPartNumber().equals(LOINC_PART_SPECIMEN_VOLUME)) {
				rt.setType(INHERES_IN);
				//And we also need to update the type map, so we can pick up the right attribute for terming
				typeMap.put(LOINC_PART_TYPE_SYSTEM, INHERES_IN);
			}
		}

		//Rule 2.f.ii.6.a.i.1.a.i
		//When the property is 'type' and the suffix indicates a biophage, then the technique
		//will come from the suffix, rather than the method
		if (loincDetail.getPartTypeName().equals(LOINC_PART_TYPE_PROPERTY) &&
				getLoincDetailForPartType(LOINC_PART_TYPE_PROPERTY).getPartNumber().equals(TYPE_ID_EXCEPTION) &&
				hasDetailForColName(COMPNUMSUFFIX_PN) &&
				BioPhageSuffixExceptions.contains(getLoincDetailOrThrow(COMPNUMSUFFIX_PN).getPartNumber())) {
			String partNum = getLoincDetailOrThrow(COMPNUMSUFFIX_PN).getPartNumber();
			List<RelationshipTemplate> additionalAttributes = cpm.getAttributePartManager().getPartMappedAttributeForType(this, partNum, typeMap.get(LOINC_PART_TYPE_METHOD));
			attributes.addAll(additionalAttributes);
			addProcessingFlag(ProcessingFlag.SUPPRESS_METHOD_TERM);
		}

		//Rule 9 Include “observation” in the Inheres in value slot of the descriptions when PROPERTY is Prid and
		// LOINC Component is LP442509-8 Observation.
		if (loincDetail.getLDTColumnName().equals(COMPNUM_PN)
				&& loincDetail.getPartNumber().equals(LOINC_PART_OBSERVATION)) {
			slotTermAppendMap.put(LOINC_PART_TYPE_COMPONENT, " observation");
		}

		super.applyTemplateSpecificModellingRules(attributes, loincDetail, rt);
	}

}
