package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;

public class LoincTemplatedConceptWithRatio extends LoincTemplatedConcept {

	private static final String SEPARATOR = "#SEPARATOR#";

	private LoincTemplatedConceptWithRatio(String loincNum) {
		super(loincNum);
	}

	@Override
	protected Concept getParentConceptForTemplate() throws TermServerScriptException {
		return gl.getConcept("540131010000107 |Ratio observable (observable entity)|");
	}

	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithRatio templatedConcept = new LoincTemplatedConceptWithRatio(loincNum);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		//The 'to' changes to a slash in the PT
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT]" + SEPARATOR + "[DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected void applyTemplateSpecificRules(Description d) {
		//If this is the FSN we separate with 'to'.
		//For the PT, speparate with '/'
		if (d.getType().equals(DescriptionType.FSN)) {
			d.setTerm(d.getTerm().replace(SEPARATOR, " to "));
		} else {
			d.setTerm(d.getTerm().replace(SEPARATOR, "/"));
		}
	}
}
