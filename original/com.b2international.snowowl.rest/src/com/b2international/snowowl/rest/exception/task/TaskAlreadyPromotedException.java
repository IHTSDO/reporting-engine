/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.task;

import com.b2international.snowowl.rest.exception.BadRequestException;

/**
 * Thrown when an editing task can not be written because it is already promoted and closed for modification.
 * 
 * @author Andras Peteri
 */
public class TaskAlreadyPromotedException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public TaskAlreadyPromotedException(final String message) {
		super(message);
	}
}
