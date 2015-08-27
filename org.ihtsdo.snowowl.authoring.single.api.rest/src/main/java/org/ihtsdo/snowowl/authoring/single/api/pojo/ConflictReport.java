package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.List;

import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;

public class ConflictReport {

	String projectReviewId;
	String taskReviewId;
	private List<ReviewConcept> concepts;
	
	public String getProjectReviewId() {
		return projectReviewId;
	}
	public void setProjectReviewId(String projectReviewId) {
		this.projectReviewId = projectReviewId;
	}
	public String getTaskReviewId() {
		return taskReviewId;
	}
	public void setTaskReviewId(String taskReviewId) {
		this.taskReviewId = taskReviewId;
	}
	public List<ReviewConcept> getConcepts() {
		return concepts;
	}
	public void setConcepts(List<ReviewConcept> concepts) {
		this.concepts = concepts;
	}
}
