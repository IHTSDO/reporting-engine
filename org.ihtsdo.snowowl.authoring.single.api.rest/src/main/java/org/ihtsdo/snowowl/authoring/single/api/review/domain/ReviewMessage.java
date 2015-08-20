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

	@ElementCollection(fetch = FetchType.EAGER)
	private List<String> subjectConceptIds;

	protected ReviewMessage() {
		creationDate = new Date();
	}

	public ReviewMessage(Branch branch, String messageHtml, List<String> subjectConceptIds, String fromUsername) {
		this();
		this.branch = branch;
		this.messageHtml = messageHtml;
		this.subjectConceptIds = subjectConceptIds;
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

	public List<String> getSubjectConceptIds() {
		return subjectConceptIds;
	}

	public Branch getBranch() {
		return branch;
	}
}
