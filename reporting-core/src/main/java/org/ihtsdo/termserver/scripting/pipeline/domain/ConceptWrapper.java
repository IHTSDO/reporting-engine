package org.ihtsdo.termserver.scripting.pipeline.domain;

import org.ihtsdo.termserver.scripting.domain.Concept;

public interface ConceptWrapper {
	public String getWrappedId();
	public void setConcept(Concept c);
	public Concept getConcept();
}
