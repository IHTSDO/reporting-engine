/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain.task;

import java.util.Date;

import com.b2international.snowowl.rest.domain.task.ITask;
import com.b2international.snowowl.rest.domain.task.TaskState;

/**
 * @author apeteri
 */
public class Task implements ITask {

	private String description;

	private String taskId;
	private Date baseTimestamp;
	private Date lastUpdatedTimestamp;
	private TaskState state = TaskState.NOT_SYNCHRONIZED;

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getTaskId() {
		return taskId;
	}

	@Override
	public Date getBaseTimestamp() {
		return baseTimestamp;
	}

	@Override
	public Date getLastUpdatedTimestamp() {
		return lastUpdatedTimestamp;
	}
	
	@Override
	public TaskState getState() {
		return state;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public void setTaskId(final String taskId) {
		this.taskId = taskId;
	}

	public void setBaseTimestamp(final Date baseTimestamp) {
		this.baseTimestamp = baseTimestamp;
	}

	public void setLastUpdatedTimestamp(final Date lastUpdatedTimestamp) {
		this.lastUpdatedTimestamp = lastUpdatedTimestamp;
	}
	
	public void setState(TaskState state) {
		this.state = state;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("Task [description=");
		builder.append(description);
		builder.append(", taskId=");
		builder.append(taskId);
		builder.append(", baseTimestamp=");
		builder.append(baseTimestamp);
		builder.append(", lastUpdatedTimestamp=");
		builder.append(lastUpdatedTimestamp);
		builder.append(", state=");
		builder.append(state);
		builder.append("]");
		return builder.toString();
	}
}
