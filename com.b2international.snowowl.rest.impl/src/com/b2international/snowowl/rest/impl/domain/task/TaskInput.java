/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain.task;

import com.b2international.snowowl.rest.domain.task.ITaskInput;

/**
 * @author apeteri
 */
public class TaskInput implements ITaskInput {

	private String description;
	private String taskId;

	@Override
	public String getDescription() {
		return description;
	}
	
	@Override
	public String getTaskId() {
		return taskId;
	}

	public void setDescription(final String description) {
		this.description = description;
	}
	
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("TaskInput [description=");
		builder.append(description);
		builder.append("]");
		return builder.toString();
	}
}
