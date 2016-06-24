package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class Notification {

	private String project;
	private String task;
	private EntityType entityType;
	private String event;
	private String branchPath;

	//Task level notification
	public Notification(String project, String task, EntityType entityType, String event) {
		this(project, entityType, event);
		this.task = task;
	}

	//Project level notification
	public Notification(String project, EntityType entityType, String event) {
		this.project = project;
		this.entityType = entityType;
		this.event = event;
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}

	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(EntityType entityType) {
		this.entityType = entityType;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public String getBranchPath() {
		return branchPath;
	}

	@Override
	public String toString() {
		return "Notification{" +
				"project='" + project + '\'' +
				", task='" + task + '\'' +
				", branchPath='" + branchPath + '\'' +
				", entityType=" + entityType +
				", event='" + event + '\'' +
				'}';
	}
}
