package org.ihtsdo.snowowl.authoring.single.api.review.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Branch {

	@Id
	@GeneratedValue(strategy= GenerationType.AUTO)
	@JsonIgnore
	private long id;
	private String project;
	private String task;

	protected Branch() {
	}

	public Branch(String project, String task) {
		this.project = project;
		this.task = task;
	}

	public Branch(String project) {
		this.project = project;
	}

	public long getId() {
		return id;
	}

	public String getProject() {
		return project;
	}

	public String getTask() {
		return task;
	}
}
