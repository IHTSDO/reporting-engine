package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.termserver.scripting.domain.Concept;

public class TemplatedConceptNull extends TemplatedConcept {

	private TemplatedConceptNull(String loincNum) {
		super(loincNum);
	}

	public static TemplatedConcept create(String loincNum) {
		TemplatedConceptNull templatedConcept = new TemplatedConceptNull(loincNum);
		templatedConcept.setIterationIndicator(IterationIndicator.REMOVED);
		return templatedConcept;
	}

	@Override
	public boolean isHighUsage() {
		return false;
	}

	@Override
	public boolean isHighestUsage() {
		return false;
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
}
