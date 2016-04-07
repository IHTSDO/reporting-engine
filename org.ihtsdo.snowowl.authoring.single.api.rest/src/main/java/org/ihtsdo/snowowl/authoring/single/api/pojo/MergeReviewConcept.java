package org.ihtsdo.snowowl.authoring.single.api.pojo;

import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;

public class MergeReviewConcept {

	private final String conceptId;
	private final String term;
	private final ChangeType changeType;

	public MergeReviewConcept(String conceptId, String term, ChangeType changeType) {
		this.conceptId = conceptId;
		this.term = term;
		this.changeType = changeType;
	}

	public String getId() {
		return conceptId;
	}

	public String getTerm() {
		return term;
	}

	public ChangeType getChangeType() {
		return changeType;
	}
}
