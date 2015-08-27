package org.ihtsdo.snowowl.authoring.single.api.review.pojo;

import java.util.ArrayList;
import java.util.List;

public class AuthoringTaskReview {

	private List<ReviewConcept> concepts;
	private String reviewId;

	public AuthoringTaskReview() {
		concepts = new ArrayList<>();
	}

	public void addConcept(ReviewConcept concept) {
		concepts.add(concept);
	}

	public List<ReviewConcept> getConcepts() {
		return concepts;
	}

	public void setConcepts(List<ReviewConcept> concepts) {
		this.concepts = concepts;
	}

	public String getReviewId() {
		return reviewId;
	}

	public void setReviewId(String reviewId) {
		this.reviewId = reviewId;
	}
}
