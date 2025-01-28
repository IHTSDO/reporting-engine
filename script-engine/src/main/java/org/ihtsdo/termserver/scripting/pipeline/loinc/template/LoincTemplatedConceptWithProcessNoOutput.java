package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

public class LoincTemplatedConceptWithProcessNoOutput extends LoincTemplatedConceptWithProcess {

	private static Concept characterizes;

	private LoincTemplatedConceptWithProcessNoOutput(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithProcessNoOutput templatedConcept = new LoincTemplatedConceptWithProcessNoOutput(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		if (characterizes == null) {
			characterizes = gl.getConcept("704321009 |Characterizes (attribute)|");
		}
		templatedConcept.typeMap.put(LOINC_PART_TYPE_COMPONENT, characterizes);

		//See https://confluence.ihtsdotools.org/display/SCTEMPLATES/Process+Observable+for+LOINC+-+No+Process+output%2C+With+Time+Aspect+%28observable+entity%29+-+v0.1
		//[property] of [characterizes] of [process output] in [process duration] in [direct site] by [technique] using [using device] [precondition] (observable entity)
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] at [TIME] in [SYSTEM] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

}
