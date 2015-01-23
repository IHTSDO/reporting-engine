/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.task;

/**
 * Thrown when an editing task could not be created successfully.
 * 
 * @author Andras Peteri
 */
public class TaskCreationException extends TaskException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public TaskCreationException(final String message) {
		super(message);
	}
}
