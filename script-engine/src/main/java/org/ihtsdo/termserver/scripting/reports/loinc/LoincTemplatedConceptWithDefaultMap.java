package org.ihtsdo.termserver.scripting.reports.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;

public class LoincTemplatedConceptWithDefaultMap extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithDefaultMap(String loincNum) {
		super(loincNum);
	}
	
	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConcept templatedConcept = new LoincTemplatedConceptWithDefaultMap(loincNum);
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] to [DIVISOR] in [SYSTEM] at [TIME] by [METHOD] using [using device] [precondition]";
		return templatedConcept;
	}

}
