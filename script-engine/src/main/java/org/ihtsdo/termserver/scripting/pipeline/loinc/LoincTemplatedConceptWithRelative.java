package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

public class LoincTemplatedConceptWithRelative extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithRelative(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithRelative templatedConcept = new LoincTemplatedConceptWithRelative(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		templatedConcept.typeMap.put("UNITS", gl.getConcept("415067009 |Percentage unit (qualifier value)|"));
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] to [DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

}
