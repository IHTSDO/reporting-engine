package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class UserFocusRequest {

	private String projectId;
	private String taskId;

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
}
