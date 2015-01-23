/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

/**
 * Thrown when an index snapshot for a supporting index can not be released for any reason.
 * 
 * @author Andras Peteri
 */
public class SnapshotReleaseException extends SnapshotException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new snapshot release exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public SnapshotReleaseException(final String message) {
		super(message);
	}
}
