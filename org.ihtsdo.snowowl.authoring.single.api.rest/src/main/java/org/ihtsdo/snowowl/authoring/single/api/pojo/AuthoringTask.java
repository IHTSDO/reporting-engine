package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonRawValue;
import net.rcarz.jiraclient.Issue;

public class AuthoringTask implements AuthoringTaskCreateRequest {

	private String key;
	private String projectKey;
	private String summary;
	private String status;
	private String description;
	private User assignee;
	private String created;
	private String updated;
	private String latestClassificationJson;
	private String latestValidationStatus;

	public AuthoringTask() {
	}

	public AuthoringTask(Issue issue) {
		key = issue.getKey();
		projectKey = issue.getProject().getKey();
		summary = issue.getSummary();
		status = issue.getStatus().getName();
		description = issue.getDescription();
		net.rcarz.jiraclient.User assignee = issue.getAssignee();
		if (assignee != null) {
			this.assignee = new User(assignee);
		}
		created = (String) issue.getField("created");
		updated = (String) issue.getField("updated");
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	@Override
	public String getSummary() {
		return summary;
	}

	@Override
	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	public User getAssignee() {
		return assignee;
	}

	public void setAssignee(User assignee) {
		this.assignee = assignee;
	}

	public String getCreated() {
		return created;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public String getUpdated() {
		return updated;
	}

	public void setUpdated(String updated) {
		this.updated = updated;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}
	
	public void setLatestClassificationJson(String json) {
		latestClassificationJson = json;
	}

	public void setLatestValidationStatus(String latestValidationStatus) {
		this.latestValidationStatus = latestValidationStatus;
	}

	public String getLatestValidationStatus() {
		return latestValidationStatus;
	}
}
