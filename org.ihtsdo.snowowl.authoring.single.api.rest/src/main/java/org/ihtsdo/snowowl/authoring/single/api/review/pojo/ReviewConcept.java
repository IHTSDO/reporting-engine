package org.ihtsdo.snowowl.authoring.single.api.review.pojo;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;

import java.util.Date;
import java.util.List;

public class ReviewConcept {

	private String id;
	private List<ReviewMessage> messages;
	private Date viewDate;

	public ReviewConcept(String id, List<ReviewMessage> messages, Date viewDate) {
		this.id = id;
		this.messages = messages;
		this.viewDate = viewDate;
	}

	public String getId() {
		return id;
	}

	public List<ReviewMessage> getMessages() {
		return messages;
	}

	public Date getViewDate() {
		return viewDate;
	}
}
