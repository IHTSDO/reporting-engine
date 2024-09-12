package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;

public class TemplatedConceptNull extends TemplatedConcept {

	private TemplatedConceptNull(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static TemplatedConceptNull create(ExternalConcept externalConcept) {
		TemplatedConceptNull templatedConcept = new TemplatedConceptNull(externalConcept);
		templatedConcept.setIterationIndicator(IterationIndicator.REMOVED);
		return templatedConcept;
	}
	
	public static TemplatedConceptNull createNull(String externalIdentifier, String property) {
		ExternalConceptNull nullExternalConcept = new ExternalConceptNull(externalIdentifier);
		nullExternalConcept.setProperty(property);
		return create(nullExternalConcept);
	}

	@Override
	public String getWrappedId() {
		return null;
	}

	@Override
	public void setConcept(Concept c) {
		throw new IllegalArgumentException("TemplatedConceptNull cannot be used to set a concept");
	}

	@Override
	public Concept getConcept() {
		return null;
	}

	@Override
	protected String getSemTag() {
		throw new IllegalArgumentException("Unexpected use of Null Templated Concept");
	}

	@Override
	protected void applyTemplateSpecificRules(Description pt) {
		throw new IllegalArgumentException("Unexpected use of Null Templated Concept");
	}

	@Override
	protected String tidyUpTerm(String ptTemplateStr) {
		throw new IllegalArgumentException("Unexpected use of Null Templated Concept");
	}

	@Override
	protected String populateTermTemplateFromSlots(String ptTemplateStr) {
		throw new IllegalArgumentException("Unexpected use of Null Templated Concept");
	}

	@Override
	protected void populateParts() {
		throw new IllegalArgumentException("Unexpected use of Null Templated Concept");
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		throw new IllegalArgumentException("Unexpected use of Null Templated Concept");
	}
}
