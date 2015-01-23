/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain.task;

import com.b2international.snowowl.rest.domain.task.ITaskChangeRequest;
import com.b2international.snowowl.rest.domain.task.TaskState;

/**
 * @author mczotter
 */
public class TaskChangeRequest implements ITaskChangeRequest {

	private TaskState state;

	@Override
	public TaskState getState() {
		return state;
	}
	
	public void setState(TaskState state) {
		this.state = state;
	}

}
