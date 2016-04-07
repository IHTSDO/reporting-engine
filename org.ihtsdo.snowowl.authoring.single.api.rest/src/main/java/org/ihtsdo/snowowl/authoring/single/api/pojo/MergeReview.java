package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.ArrayList;
import java.util.List;

public class MergeReview {

	private List<MergeReviewConcept> concepts;
	private String reviewId;

	public MergeReview() {
		concepts = new ArrayList<>();
	}

	public void addConcept(MergeReviewConcept concept) {
		concepts.add(concept);
	}

	public List<MergeReviewConcept> getConcepts() {
		return concepts;
	}

	public String getReviewId() {
		return reviewId;
	}

	public void setReviewId(String reviewId) {
		this.reviewId = reviewId;
	}
}
