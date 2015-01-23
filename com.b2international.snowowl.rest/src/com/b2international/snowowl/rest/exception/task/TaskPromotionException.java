/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.task;

/**
 * Thrown when changes on a task could not be promoted to the parent version successfully.
 * 
 * @author Andras Peteri
 */
public class TaskPromotionException extends TaskException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public TaskPromotionException(final String message) {
		super(message);
	}
}
