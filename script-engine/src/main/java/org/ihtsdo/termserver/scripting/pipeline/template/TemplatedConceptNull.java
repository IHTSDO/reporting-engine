package org.ihtsdo.termserver.scripting.pipeline.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConceptNull;

public class TemplatedConceptNull extends TemplatedConcept {

	private static final String UNEXPECTED = "Unexpected use of Null Templated Concept";

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
	protected String getSemTag() {
		throw new IllegalArgumentException(UNEXPECTED);
	}

	@Override
	public String getSchemaId() {
		throw new IllegalArgumentException(UNEXPECTED);
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description pt) {
		throw new IllegalArgumentException(UNEXPECTED);
	}

	@Override
	protected String tidyUpTerm(String ptTemplateStr) {
		throw new IllegalArgumentException(UNEXPECTED);
	}

	@Override
	protected String populateTermTemplateFromSlots(String ptTemplateStr) {
		throw new IllegalArgumentException(UNEXPECTED);
	}

	@Override
	protected void populateParts() {
		throw new IllegalArgumentException(UNEXPECTED);
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		throw new IllegalArgumentException(UNEXPECTED);
	}

}
