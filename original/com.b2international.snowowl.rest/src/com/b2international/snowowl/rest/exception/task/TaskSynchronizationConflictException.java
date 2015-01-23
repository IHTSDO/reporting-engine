/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.task;

/**
 * Thrown when an editing task could not be synchronized with the parent version's contents because of conflicting edits. 
 * 
 * @author Andras Peteri
 */
public class TaskSynchronizationConflictException extends TaskSynchronizationException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public TaskSynchronizationConflictException(final String message) {
		super(message);
	}
}
