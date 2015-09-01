package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.List;

public class ConflictReport {

	String sourceReviewId;
	String targetReviewId;
	private List<ConceptConflict> concepts;
	
	public String getSourceReviewId() {
		return sourceReviewId;
	}
	public void setSourceReviewId(String sourceReviewId) {
		this.sourceReviewId = sourceReviewId;
	}
	public String getTargetReviewId() {
		return targetReviewId;
	}
	public void setTargetReviewId(String targetReviewId) {
		this.targetReviewId = targetReviewId;
	}
	public List<ConceptConflict> getConcepts() {
		return concepts;
	}
	public void setConcepts(List<ConceptConflict> concepts) {
		this.concepts = concepts;
	}
	

}
