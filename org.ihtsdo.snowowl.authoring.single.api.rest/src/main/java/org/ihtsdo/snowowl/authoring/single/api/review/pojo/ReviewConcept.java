package org.ihtsdo.snowowl.authoring.single.api.review.pojo;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;

import java.util.List;

public class ReviewConcept {

	private String id;
	private String term;
	private ChangeType changeType;
	private List<ReviewMessage> messages;
	private boolean read;

	public ReviewConcept(String id, String term, ChangeType changeType) {
		this.id = id;
		this.term = term;
		this.changeType = changeType;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public ChangeType getChangeType() {
		return changeType;
	}

	public void setChangeType(ChangeType changeType) {
		this.changeType = changeType;
	}

	public void setMessages(List<ReviewMessage> messages) {
		this.messages = messages;
	}

	public List<ReviewMessage> getMessages() {
		return messages;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}
}
