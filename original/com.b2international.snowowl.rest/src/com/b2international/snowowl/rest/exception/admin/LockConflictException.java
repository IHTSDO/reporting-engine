/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

/**
 * Thrown when an operation lock can not be acquired because someone else has already taken a conflicting lock.
 * 
 * @author Andras Peteri
 */
public class LockConflictException extends LockException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception instance with the specified message.
	 * @param message the exception message
	 */
	public LockConflictException(final String message) {
		super(message);
	}
}
