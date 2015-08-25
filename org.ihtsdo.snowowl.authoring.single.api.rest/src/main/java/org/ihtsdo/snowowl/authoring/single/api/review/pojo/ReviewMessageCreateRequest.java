package org.ihtsdo.snowowl.authoring.single.api.review.pojo;

import java.util.List;

public class ReviewMessageCreateRequest {

	private String messageHtml;

	private List<String> subjectConceptIds;

	private boolean feedbackRequested;

	public ReviewMessageCreateRequest() {
	}

	public String getMessageHtml() {
		return messageHtml;
	}

	public void setMessageHtml(String messageHtml) {
		this.messageHtml = messageHtml;
	}

	public List<String> getSubjectConceptIds() {
		return subjectConceptIds;
	}

	public void setSubjectConceptIds(List<String> subjectConceptIds) {
		this.subjectConceptIds = subjectConceptIds;
	}

	public boolean isFeedbackRequested() {
		return feedbackRequested;
	}

	public void setFeedbackRequested(boolean feedbackRequested) {
		this.feedbackRequested = feedbackRequested;
	}
}
