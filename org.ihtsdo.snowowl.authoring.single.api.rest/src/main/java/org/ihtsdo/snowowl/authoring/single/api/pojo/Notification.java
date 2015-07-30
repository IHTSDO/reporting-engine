package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class Notification {

	private String project;
	private String task;
	private EntityType entityType;
	private String event;

	public Notification(String project, String task, EntityType entityType, String event) {
		this.project = project;
		this.task = task;
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

	@Override
	public String toString() {
		return "Notification{" +
				"project='" + project + '\'' +
				", task='" + task + '\'' +
				", entityType=" + entityType +
				", event='" + event + '\'' +
				'}';
	}
}
