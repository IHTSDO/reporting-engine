/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

/**
 * Thrown when index snapshot contents can not be listed for a supporting index.
 * 
 * @author Andras Peteri
 */
public class SnapshotListingException extends SnapshotException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new snapshot listing exception instance with the specified message.
	 * 
	 * @param message the exception message
	 */
	public SnapshotListingException(final String message) {
		super(message);
	}
}
