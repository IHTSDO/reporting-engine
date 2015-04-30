package org.ihtsdo.snowowl.authoring.api.model.work;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class ContentValidationResult {

	private List<ConceptValidationResult> conceptResults;

	public ContentValidationResult() {
		conceptResults = new ArrayList<>();
	}

	@JsonIgnore
	public ConceptResultFactory getConceptResultFactory() {
		return new ConceptResultFactory(conceptResults);
	}

	public List<ConceptValidationResult> getConceptResults() {
		return conceptResults;
	}

	public void setConceptResults(List<ConceptValidationResult> conceptResults) {
		this.conceptResults = conceptResults;
	}
}
