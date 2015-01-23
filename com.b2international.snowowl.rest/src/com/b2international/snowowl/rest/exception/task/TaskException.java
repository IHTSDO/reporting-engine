/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.task;

/**
 * Common superclass for task maintenance-related exceptions.
 * 
 * @author Andras Peteri
 */
public abstract class TaskException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified exception message.
	 * 
	 * @param message the exception message
	 */
	protected TaskException(final String message) {
		super(message);
	}
}
