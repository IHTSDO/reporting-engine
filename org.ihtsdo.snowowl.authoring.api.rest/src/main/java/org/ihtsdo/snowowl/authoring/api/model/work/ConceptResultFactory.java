package org.ihtsdo.snowowl.authoring.api.model.work;

import java.util.List;

public class ConceptResultFactory {

	private final List<ConceptValidationResult> conceptResults;
	private int offset;

	public ConceptResultFactory(List<ConceptValidationResult> conceptResults) {
		this.conceptResults = conceptResults;
		offset = -1;
	}

	public ConceptValidationResult next() {
		offset++;
		if (conceptResults.size() < offset + 1) {
			conceptResults.add(new ConceptValidationResult());
		}
		return conceptResults.get(offset);
	}
}
