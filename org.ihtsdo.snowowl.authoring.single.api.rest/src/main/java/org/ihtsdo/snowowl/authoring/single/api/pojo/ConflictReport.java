package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	@JsonIgnore
	public String getCompositeId() {
		return sourceReviewId + "|" + targetReviewId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ConflictReport that = (ConflictReport) o;

		if (sourceReviewId != null ? !sourceReviewId.equals(that.sourceReviewId) : that.sourceReviewId != null) return false;
		return !(targetReviewId != null ? !targetReviewId.equals(that.targetReviewId) : that.targetReviewId != null);

	}

	@Override
	public int hashCode() {
		int result = sourceReviewId != null ? sourceReviewId.hashCode() : 0;
		result = 31 * result + (targetReviewId != null ? targetReviewId.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "ConflictReport{" +
				"sourceReviewId='" + sourceReviewId + '\'' +
				", targetReviewId='" + targetReviewId + '\'' +
				'}';
	}
}
