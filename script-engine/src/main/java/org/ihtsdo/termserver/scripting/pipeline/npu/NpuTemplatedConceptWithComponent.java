package org.ihtsdo.termserver.scripting.pipeline.npu;


import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

public class NpuTemplatedConceptWithComponent extends NpuTemplatedConcept {

	private NpuTemplatedConceptWithComponent(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static NpuTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		NpuTemplatedConceptWithComponent templatedConcept = new NpuTemplatedConceptWithComponent(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.setPreferredTermTemplate("[SCALE] of [PROPERTY] of [COMPONENT] in [SYSTEM] measured by [UNIT] ");
		return templatedConcept;
	}

}
