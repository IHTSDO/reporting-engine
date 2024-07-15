package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

import java.util.ArrayList;
import java.util.List;

public class LoincTemplatedConceptWithProcess extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithProcess(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithProcess templatedConcept = new LoincTemplatedConceptWithProcess(loincNum);
		templatedConcept.typeMap.put(LOINC_PART_TYPE_PROPERTY, gl.getConcept("370130000 |Property (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_SCALE, gl.getConcept("370132008 |Scale type (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_TIME, gl.getConcept("704323007 |Process duration (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_SYSTEM, gl.getConcept("704327008 |Direct site (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_METHOD, gl.getConcept("246501002 |Technique (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, gl.getConcept("704320005 |Towards (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_DEVICE, gl.getConcept("424226004 |Using device (attribute)|"));
		templatedConcept.typeMap.put(LOINC_PART_TYPE_CHALLENGE, precondition);
		
		templatedConcept.preferredTermTemplate = "[PROPERTY] to [COMPONENT] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues) throws TermServerScriptException {
		//Following the rules detailed in https://docs.google.com/document/d/1rz2s3ga2dpdwI1WVfcQMuRXWi5RgpJOIdicgOz16Yzg/edit
		//With respect to the values read from Loinc_Detail_Type_1 file

		List<RelationshipTemplate> attributes = new ArrayList<>();
		Concept componentAttribType = typeMap.get(LOINC_PART_TYPE_COMPONENT);

		//We can't yet deal with "given"
		if (detailPresent(loincNum, COMPNUM_PN) &&
			getLoincDetail(loincNum, COMPNUM_PN).getPartName().endsWith(" given")) {
			String issue = "Skipping concept using 'given'";
			cpm.report(getTab(TAB_IOI), issue, loincNum);
			issues.add(issue);
			attributes.add(null);
			return attributes;
		}

		if (CompNumPnIsSafe(loincNum)) {
			//Use COMPNUM_PN LOINC Part map to model SCT Component
			addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttribType);
		} else {
			processSubComponents(loincNum, attributes, issues, componentAttribType);
		}

		//If we didn't find the component, return a null so that we record that failed mapping usage
		//And in fact, don't map this term at all
		if (attributes.isEmpty()) {
			attributes.add(null);
			processingFlags.add(ProcessingFlag.DROP_OUT);
		}
		return attributes;
	}

	private void processSubComponents(String loincNum, List<RelationshipTemplate> attributes, List<String> issues, Concept componentAttribType) throws TermServerScriptException {
		if (detailPresent(loincNum, COMPSUBPART2_PN)) {
			if(attributes.isEmpty()) {
				addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttribType);
			}
			addAttributeFromDetailWithType(attributes, COMPSUBPART2_PN, issues, precondition);
		}

		if (attributes.isEmpty() && detailPresent(loincNum, COMPSUBPART3_PN)) {
			addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttribType);
			LoincDetail componentDetail = getLoincDetail(loincNum, COMPSUBPART3_PN);
			slotTermAppendMap.put(LOINC_PART_TYPE_COMPONENT, componentDetail.getPartName());

		}

		if (attributes.isEmpty() && detailPresent(loincNum, COMPSUBPART4_PN)) {
			addAttributeFromDetailWithType(attributes, COMPNUM_PN, issues, componentAttribType);
			LoincDetail componentDetail = getLoincDetail(loincNum, COMPSUBPART4_PN);
			slotTermAppendMap.put(LOINC_PART_TYPE_COMPONENT, componentDetail.getPartName());
		}
	}

	@Override
	protected void applyTemplateSpecificRules(List<RelationshipTemplate> attributes, LoincDetail loincDetail, RelationshipTemplate rt) throws TermServerScriptException {
		//Rule v.3.4.a & b
		if (loincDetail.getLDTColumnName().equals(SYSTEM_PN)) {
			//All process observables will have an agent and characterizes.
			//But only if we're working with Urine
			if (loincDetail.getPartName().contains("Urine")) {
				Concept agent = gl.getConcept("704322002 |Process agent (attribute)|");
				Concept kidneyStruct = gl.getConcept("64033007 |Kidney structure (body structure)|");
				attributes.add(new RelationshipTemplate(agent, kidneyStruct));

				Concept characterizes = gl.getConcept("704321009 |Characterizes (attribute)|");
				Concept excrtProc = gl.getConcept("718500008 |Excretory process (qualifier value)| ");
				attributes.add(new RelationshipTemplate(characterizes, excrtProc));
			} else {
				addIssue("System not recognised as Urine in Process Observable: " + loincDetail.getPartName());
				processingFlags.add(ProcessingFlag.DROP_OUT);
			}
		}

		super.applyTemplateSpecificRules(attributes, loincDetail, rt);
	}
}
