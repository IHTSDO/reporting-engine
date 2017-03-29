
package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
	"assignee",
	"branchPath",
	"branchState",
	"created",
	"description",
	"feedbackMessageDate",
	"feedbackMessagesStatus",
	"key",
	"labels",
	"latestClassificationJson",
	"latestValidationStatus",
	"projectKey",
	"reviewer",
	"status",
	"summary",
	"updated",
	"viewDate"
})
public class AuthoringTask implements AuthoringTaskCreateRequest, AuthoringTaskUpdateRequest{

	@JsonProperty("assignee")
	private User assignee;
	@JsonProperty("branchPath")
	private String branchPath;
	@JsonProperty("branchState")
	private String branchState;
	@JsonProperty("created")
	private String created;
	@JsonProperty("description")
	private String description;
	@JsonProperty("feedbackMessageDate")
	private String feedbackMessageDate;
	@JsonProperty("feedbackMessagesStatus")
	private String feedbackMessagesStatus;
	@JsonProperty("key")
	private String key;
	@JsonProperty("labels")
	private String[] labels;
	@JsonProperty("latestClassificationJson")
	private String latestClassificationJson;
	@JsonProperty("latestValidationStatus")
	private String latestValidationStatus;
	@JsonProperty("projectKey")
	private String projectKey;
	@JsonProperty("reviewer")
	private User reviewer;
	@JsonProperty("status")
	private String status;
	@JsonProperty("summary")
	private String summary;
	@JsonProperty("updated")
	private String updated;
	@JsonProperty("viewDate")
	private String viewDate;
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	@JsonProperty("assignee")
	public User getAssignee() {
		return assignee;
	}

	@JsonProperty("assignee")
	public void setAssignee(User assignee) {
		this.assignee = assignee;
	}

	@JsonProperty("branchPath")
	public String getBranchPath() {
		return branchPath;
	}

	@JsonProperty("branchPath")
	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	@JsonProperty("branchState")
	public String getBranchState() {
		return branchState;
	}

	@JsonProperty("branchState")
	public void setBranchState(String branchState) {
		this.branchState = branchState;
	}

	@JsonProperty("created")
	public String getCreated() {
		return created;
	}

	@JsonProperty("created")
	public void setCreated(String created) {
		this.created = created;
	}

	@JsonProperty("description")
	public String getDescription() {
		return description;
	}

	@JsonProperty("description")
	public void setDescription(String description) {
		this.description = description;
	}

	@JsonProperty("feedbackMessageDate")
	public String getFeedbackMessageDate() {
		return feedbackMessageDate;
	}

	@JsonProperty("feedbackMessageDate")
	public void setFeedbackMessageDate(String feedbackMessageDate) {
		this.feedbackMessageDate = feedbackMessageDate;
	}

	@JsonProperty("feedbackMessagesStatus")
	public String getFeedbackMessagesStatus() {
		return feedbackMessagesStatus;
	}

	@JsonProperty("feedbackMessagesStatus")
	public void setFeedbackMessagesStatus(String feedbackMessagesStatus) {
		this.feedbackMessagesStatus = feedbackMessagesStatus;
	}

	@JsonProperty("key")
	public String getKey() {
		return key;
	}

	@JsonProperty("key")
	public void setKey(String key) {
		this.key = key;
	}

	@JsonProperty("labels")
	public String[] getLabels() {
		return labels;
	}

	@JsonProperty("labels")
	public void setLabels(String[] labels) {
		this.labels = labels;
	}

	@JsonProperty("latestClassificationJson")
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}

	@JsonProperty("latestClassificationJson")
	public void setLatestClassificationJson(String latestClassificationJson) {
		this.latestClassificationJson = latestClassificationJson;
	}

	@JsonProperty("latestValidationStatus")
	public String getLatestValidationStatus() {
		return latestValidationStatus;
	}

	@JsonProperty("latestValidationStatus")
	public void setLatestValidationStatus(String latestValidationStatus) {
		this.latestValidationStatus = latestValidationStatus;
	}

	@JsonProperty("projectKey")
	public String getProjectKey() {
		return projectKey;
	}

	@JsonProperty("projectKey")
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	@JsonProperty("reviewer")
	public User getReviewer() {
		return reviewer;
	}

	@JsonProperty("reviewer")
	public void setReviewer(User reviewer) {
		this.reviewer = reviewer;
	}

	@JsonProperty("status")
	public String getStatus() {
		return status;
	}

	@JsonProperty("status")
	public void setStatus(String status) {
		this.status = status;
	}

	@JsonProperty("summary")
	public String getSummary() {
		return summary;
	}

	@JsonProperty("summary")
	public void setSummary(String summary) {
		this.summary = summary;
	}

	@JsonProperty("updated")
	public String getUpdated() {
		return updated;
	}

	@JsonProperty("updated")
	public void setUpdated(String updated) {
		this.updated = updated;
	}

	@JsonProperty("viewDate")
	public String getViewDate() {
		return viewDate;
	}

	@JsonProperty("viewDate")
	public void setViewDate(String viewDate) {
		this.viewDate = viewDate;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
