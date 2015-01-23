/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

/**
 * Thrown when index snapshot creation fails for a supporting index.
 * 
 * @author Andras Peteri
 */
public class SnapshotCreationException extends SnapshotException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new snapshot creation exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public SnapshotCreationException(final String message) {
		super(message);
	}
}
