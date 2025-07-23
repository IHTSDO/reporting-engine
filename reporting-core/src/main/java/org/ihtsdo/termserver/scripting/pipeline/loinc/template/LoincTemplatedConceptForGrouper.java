package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;

public class LoincTemplatedConceptForGrouper extends LoincTemplatedConcept {

	private LoincTemplatedConceptForGrouper(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptForGrouper templatedConcept = new LoincTemplatedConceptForGrouper(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.setPreferredTermTemplate("[PROPERTY] to [COMPONENT] in [SYSTEM]");
		return templatedConcept;
	}

}
