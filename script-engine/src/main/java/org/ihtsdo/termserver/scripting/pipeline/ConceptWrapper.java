package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.termserver.scripting.domain.Concept;

public interface ConceptWrapper {
	public String getWrappedId();
	public void setConcept(Concept c);
	public Concept getConcept();
}
