package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

import java.util.List;
import java.util.Set;

public class LoincTemplatedConceptWithRatio extends LoincTemplatedConceptWithRelative {

	private LoincTemplatedConceptWithRatio(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	@Override
	protected Concept getParentConceptForTemplate() throws TermServerScriptException {
		return gl.getConcept("540131010000107 |Ratio observable (observable entity)|");
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithRatio templatedConcept = new LoincTemplatedConceptWithRatio(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		//The 'to' changes to a slash in the PT
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT]" + SEPARATOR + "[DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

}
