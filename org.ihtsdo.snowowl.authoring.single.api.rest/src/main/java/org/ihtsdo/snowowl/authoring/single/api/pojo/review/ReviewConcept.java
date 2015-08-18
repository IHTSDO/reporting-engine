package org.ihtsdo.snowowl.authoring.single.api.pojo.review;

public class ReviewConcept {

	private String id;
	private String term;
	private ChangeType changeType;

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
}
