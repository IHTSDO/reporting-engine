package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

public class LoincTemplatedConceptWithInheresNoComponent extends LoincTemplatedConceptWithInheres {

	private LoincTemplatedConceptWithInheresNoComponent(ExternalConcept externalConcept) {
		super(externalConcept);
	}
	
	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithInheresNoComponent templatedConcept = new LoincTemplatedConceptWithInheresNoComponent(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put(LOINC_PART_TYPE_SYSTEM, gl.getConcept("704319004 |Inheres in (attribute)|"));
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

}
