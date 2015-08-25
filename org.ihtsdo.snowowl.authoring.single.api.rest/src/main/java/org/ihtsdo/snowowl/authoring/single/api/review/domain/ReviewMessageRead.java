package org.ihtsdo.snowowl.authoring.single.api.review.domain;

import javax.persistence.*;

@Entity
public class ReviewMessageRead {

	@Id
	@GeneratedValue(strategy= GenerationType.AUTO)
	private long id;
	@ManyToOne(fetch = FetchType.EAGER)
	private ReviewMessage message;
	private String conceptId;
	private String username;

	protected ReviewMessageRead() {
	}

	public ReviewMessageRead(ReviewMessage message, String conceptId, String username) {
		this.message = message;
		this.conceptId = conceptId;
		this.username = username;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public ReviewMessage getMessage() {
		return message;
	}

	public String getUsername() {
		return username;
	}

	public String getConceptId() {
		return conceptId;
	}

	@Override
	public String toString() {
		return "ReviewMessageRead{" +
				"id=" + id +
				", message=" + message +
				", conceptId='" + conceptId + '\'' +
				", username='" + username + '\'' +
				'}';
	}
}
