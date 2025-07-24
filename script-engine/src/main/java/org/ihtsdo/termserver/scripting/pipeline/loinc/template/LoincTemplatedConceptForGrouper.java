package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;

import java.util.List;

public class LoincTemplatedConceptForGrouper extends LoincTemplatedConcept {

	private LoincTemplatedConceptForGrouper(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptForGrouper templatedConcept = new LoincTemplatedConceptForGrouper(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] in [SYSTEM]");
		return templatedConcept;
	}

	protected List<RelationshipTemplate> determineComponentAttributes() throws TermServerScriptException {
		// Rule ix.3.a.ii if we have subpart, drop out the concept
		if (!hasNoSubParts()) {
			int tab = cpm.getTab(TAB_MODELING_ISSUES);
			cpm.report(tab, getExternalIdentifier(),
				getExternalConcept().getLongDisplayName(),
				"Grouper concept features subparts");
			addProcessingFlag(ProcessingFlag.DROP_OUT);
		}
		return super.determineComponentAttributes();
	}


		@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		LoincDetail loincDetail = (LoincDetail) part;

		//Groupers which are missing a system, can be rejected
		if (loincDetail.getPartTypeName().equals(LOINC_PART_TYPE_SYSTEM) && attributes.isEmpty()) {
			addProcessingFlag(ProcessingFlag.DROP_OUT);
		}
		super.applyTemplateSpecificModellingRules(attributes, part, rt);
	}

}
