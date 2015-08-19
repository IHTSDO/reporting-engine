package org.ihtsdo.snowowl.authoring.single.api.review.domain;

import java.util.Date;
import java.util.List;
import javax.persistence.*;

@Entity
public class ReviewMessage {

	@Id
	@GeneratedValue(strategy= GenerationType.AUTO)
	private long id;
	@ManyToOne
	private Branch branch;
	private String messageHtml;
	private Date creationDate;
	private String fromUsername;

	@OneToMany
	private List<Concept> subjectConcepts;

	protected ReviewMessage() {
		creationDate = new Date();
	}

	public ReviewMessage(Branch branch, String messageHtml, List<Concept> subjectConcepts, String fromUsername) {
		this.branch = branch;
		this.messageHtml = messageHtml;
		this.subjectConcepts = subjectConcepts;
		this.fromUsername = fromUsername;
	}

	public long getId() {
		return id;
	}

	public Date getCreationDate() {
		return creationDate;
	}
	public String getFromUsername() {
		return fromUsername;
	}

	public String getMessageHtml() {
		return messageHtml;
	}

	public List<Concept> getSubjectConcepts() {
		return subjectConcepts;
	}

	public Branch getBranch() {
		return branch;
	}
}
