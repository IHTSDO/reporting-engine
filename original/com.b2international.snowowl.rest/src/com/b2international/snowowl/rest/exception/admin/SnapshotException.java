/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

/**
 * Thrown when an operation related to snapshots fails for a supporting index.
 * 
 * @author Andras Peteri
 */
public abstract class SnapshotException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new snapshot exception instance.
	 */
	protected SnapshotException() {
		super();
	}

	/**
	 * Creates a new snapshot exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	protected SnapshotException(final String message) {
		super(message);
	}
}
