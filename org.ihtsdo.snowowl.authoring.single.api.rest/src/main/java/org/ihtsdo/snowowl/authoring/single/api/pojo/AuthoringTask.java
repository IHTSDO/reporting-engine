package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.b2international.snowowl.core.branch.Branch;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.rcarz.jiraclient.Issue;
import net.sf.json.JSONObject;
import org.ihtsdo.snowowl.authoring.single.api.service.PathHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskStatus;

public class AuthoringTask implements AuthoringTaskCreateRequest, AuthoringTaskUpdateRequest {

	public static final String JIRA_CREATED_FIELD = "created";
	public static final String JIRA_UPDATED_FIELD = "updated";

	public static String jiraReviewerField;

	private String key;
	private String projectKey;
	private String summary;
	private TaskStatus status;
	private Branch.BranchState branchState;
	private String description;
	private User assignee;
	private User reviewer;
	private String created;
	private String updated;
	private String branchPath;

	public AuthoringTask() {
	}

	public AuthoringTask(Issue issue, String extensionBase) {
		key = issue.getKey();
		projectKey = issue.getProject().getKey();
		summary = issue.getSummary();
		status = TaskStatus.fromLabel(issue.getStatus().getName());
		description = issue.getDescription();
		net.rcarz.jiraclient.User assignee = issue.getAssignee();
		if (assignee != null) {
			this.assignee = new User(assignee);
		}
		created = (String) issue.getField(JIRA_CREATED_FIELD);
		updated = (String) issue.getField(JIRA_UPDATED_FIELD);
		

		// set the reviewer object
		Object reviewerObj = issue.getField(jiraReviewerField);
		if (reviewerObj != null && reviewerObj instanceof JSONObject) {
			reviewer = new User((JSONObject)reviewerObj);
		}

		branchPath = PathHelper.getTaskPath(extensionBase, projectKey, key);
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

	@JsonProperty("status")
	public String getStatusName() {
		return status != null ? status.getLabel() : null;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
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

	@Override
	public User getAssignee() {
		return assignee;
	}

	@Override
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

	public User getReviewer() {
		return reviewer;
	}

	public void setReviewer(User reviewer) {
		this.reviewer = reviewer;
	}

	public void setBranchState(Branch.BranchState branchState) {
		this.branchState = branchState;
	}

	public Branch.BranchState getBranchState() {
		return branchState;
	}

	public static void setJiraReviewerField(String jiraReviewerField) {
		AuthoringTask.jiraReviewerField = jiraReviewerField;
	}

	public String getBranchPath() {
		return branchPath;
	}

}
