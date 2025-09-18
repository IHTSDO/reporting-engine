package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;

import java.util.ArrayList;
import java.util.List;

import static org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript.LOINC_PART_SEDIMENTATION;
import static org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript.LOINC_PART_SPECIMEN_VOLUME;

public class LoincTemplatedConceptWithProcess extends LoincTemplatedConcept {

	private static Concept processOutput;
	private static Concept characterizes;

	protected LoincTemplatedConceptWithProcess(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithProcess templatedConcept = new LoincTemplatedConceptWithProcess(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		if (processOutput == null) {
			processOutput = gl.getConcept("704324001 |Process output (attribute)|");
		}
		characterizes = gl.getConcept("704321009 |Characterizes (attribute)|");
		templatedConcept.typeMap.put("CHARACTERIZES", characterizes);
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, processOutput);
		templatedConcept.typeMap.put(LOINC_PART_TYPE_TIME, gl.getConcept("704323007 |Process duration (attribute)|"));

		//See https://confluence.ihtsdotools.org/display/SCTEMPLATES/Process+Observable+for+LOINC+%28observable+entity%29+-+v1.0
		//[property] of [characterizes] of [process output] in [process duration] in [direct site] by [technique] using [using device] [precondition] (observable entity)
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [CHARACTERIZES] of [COMPONENT] in [TIME] in [SYSTEM] by [METHOD] using [DEVICE] [CHALLENGE]");
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

		ensureComponentMappedOrRepresentedInTerm(attributes);
		return attributes;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		LoincDetail loincDetail = (LoincDetail) part;
		//Rule v.3.4.a
		//All process observables will have an agent and characterizes.
		//But only if we're working with Urine and the component is a type of substance
		if (loincDetail.getLDTColumnName().equals(SYSTEM_PN)
			&& loincDetail.getPartName().contains("Urine")
			&& componentIsSubstance()) {
			Concept agent = gl.getConcept("704322002 |Process agent (attribute)|");
			Concept kidneyStruct = gl.getConcept("64033007 |Kidney structure (body structure)|");
			attributes.add(new RelationshipTemplate(agent, kidneyStruct));

			Concept excreteProc = gl.getConcept("718500008 |Excretory process (qualifier value)| ");
			attributes.add(new RelationshipTemplate(characterizes, excreteProc));
			slotTermMap.put("CHARACTERIZES", "excretion");
		} else if (loincDetail.getLDTColumnName().equals(PROPERTY)
				&& loincDetail.getPartNumber().equals(LOINC_PART_SEDIMENTATION)) {
			addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			slotTermMap.put("PROPERTY", "Rate");
			Concept sedProc = gl.getConcept("580161010000105 |Sedimentation process (qualifier value)|");
			attributes.add(new RelationshipTemplate(characterizes, sedProc));
		} else {
			addProcessingFlag(ProcessingFlag.SUPPRESS_CHARACTERIZES_TERM);
		}

		if (loincDetail.getPartNumber().equals(LOINC_PART_SPECIMEN_VOLUME)) {
			//For a process template, "specimen" will appear in the SYSTEM slot.
			addProcessingFlag(ProcessingFlag.ALLOW_SPECIMEN);
			setPreferredTermTemplate("[PROPERTY] in [TIME] in [SYSTEM] by [METHOD] using [DEVICE] [CHALLENGE]");
		}

		//Rule vi.6.b LP16409-2 Erythrocyte sedimentation rate
		if (loincDetail.getLDTColumnName().equals(COMPNUM_PN) && loincDetail.getPartNumber().equals("LP16409-2")) {
			swapAttributeType(attributes, processOutput, gl.getConcept("704321009 |Characterizes (attribute)|"));
			RelationshipTemplate additionalAttribute = new RelationshipTemplate(
					gl.getConcept("1003735000 |Process acts on (attribute)|"),
					gl.getConcept("418525009 |Erythrocyte component of blood (substance)|"));
			attributes.add(additionalAttribute);
		}

		super.applyTemplateSpecificModellingRules(attributes, loincDetail, rt);
	}

	private boolean componentIsSubstance() throws TermServerScriptException {
		String loincPartNum = getLoincDetailOrThrow(COMPNUM_PN).getPartNumber();
		Concept attributeType = typeMap.get(LOINC_PART_TYPE_COMPONENT);
		List<RelationshipTemplate> attributes = cpm.getAttributePartManager().getPartMappedAttributeForType(this, loincPartNum, attributeType);
		//Return true if any attribute value fsn contains "substance"
		return attributes.stream()
				.anyMatch(rt -> rt.getTarget().getFsn().contains("(substance)"));
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		//If the CHARACTERIZES slot is still here, but we're allowing it to be empty,
		//remove it, and its connector
		if (hasProcessingFlag(ProcessingFlag.SUPPRESS_CHARACTERIZES_TERM)
				&& d.getTerm().contains("[CHARACTERIZES]")) {
			d.setTerm(d.getTerm().replace(" of [CHARACTERIZES]", ""));
		}
		super.applyTemplateSpecificTermingRules(d);
	}

	private void swapAttributeType(List<RelationshipTemplate> attributes, Concept find, Concept replace) {
		for (RelationshipTemplate rt : attributes) {
			if (rt.getType().equals(find)) {
				rt.setType(replace);
			}
		}
	}
}
