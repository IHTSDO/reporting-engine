package org.ihtsdo.snowowl.authoring.single.api.review.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import javax.persistence.*;

@Entity(name = "ReviewConceptView")
public class ReviewConceptView {

	@Id
	@GeneratedValue(strategy= GenerationType.AUTO)
	private long id;
	@ManyToOne
	private Branch branch;
	private String conceptId;
	private String username;
	private final Date viewDate;

	protected ReviewConceptView() {
		viewDate = new Date();
	}

	public ReviewConceptView(Branch branch, String conceptId, String username) {
		this();
		this.branch = branch;
		this.conceptId = conceptId;
		this.username = username;
	}

	@JsonIgnore
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	@JsonIgnore
	public Branch getBranch() {
		return branch;
	}

	public String getUsername() {
		return username;
	}

	public String getConceptId() {
		return conceptId;
	}
	
	public Date getViewDate() {
		return viewDate;
	}

	@Override
	public String toString() {
		return "ReviewMessageRead{" +
				"id=" + id +
				", conceptId='" + conceptId + '\'' +
				", username='" + username + '\'' +
				", viewDate=" + viewDate +
				'}';
	}
}
