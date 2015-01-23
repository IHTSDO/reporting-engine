/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.task;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a task can not be found for a given task identifier.
 * 
 * @author Andras Peteri
 */
public class TaskNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified task identifier.
	 * 
	 * @param taskId the identifier of the task which could not be found for a code system version (may not be {@code null})
	 */
	public TaskNotFoundException(String taskId) {
		super("Task", taskId);
	}
}
