package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Description;

public class TemplatedConceptWithDefaultMap extends TemplatedConcept {

	private String codeSystemSctId = "default";
	private String semTag = "(default)";
	private TemplatedConceptWithDefaultMap(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	@Override
	protected String getCodeSystemSctId() {
		return codeSystemSctId;
	}

	public static TemplatedConceptWithDefaultMap create(ExternalConcept externalConcept, String codeSystemSctId, String semTag) {
		TemplatedConceptWithDefaultMap templatedConcept = new TemplatedConceptWithDefaultMap(externalConcept);
		templatedConcept.codeSystemSctId = codeSystemSctId;
		templatedConcept.semTag = semTag;
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] to [DIVISOR] in [SYSTEM] at [TIME] by [METHOD] using [using device] [CHALLENGE]");
		return templatedConcept;
	}

	@Override
	public String getSemTag() {
		return semTag;
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description pt) {
		throw new IllegalStateException(getExceptionText());
	}

	private String getExceptionText() {
		return "Not expecting to use default map.  LoincNum: " + getExternalIdentifier();
	}

	@Override
	protected String populateTermTemplateFromSlots(String ptTemplateStr) {
		throw new IllegalStateException(getExceptionText());
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		throw new IllegalStateException(getExceptionText());
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		throw new IllegalStateException(getExceptionText());
	}

}
