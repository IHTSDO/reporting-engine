package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincTemplatedConceptWithRelative extends LoincTemplatedConcept {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConceptWithRelative.class);

	private LoincTemplatedConceptWithRelative(String loincNum) {
		super(loincNum);
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithRelative templatedConcept = new LoincTemplatedConceptWithRelative(loincNum);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		templatedConcept.typeMap.put("UNITS", gl.getConcept("415067009 |Percentage unit (qualifier value)|"));
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] to [DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

}
