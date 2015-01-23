/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

/**
 * Specific {@link StoreException} when the transaction cannot be processed due to a lock existing on the underlying repository. The requester has to
 * sent the request again, after the locked was released.
 * 
 * @author mczotter
 * @since 1.0
 */
public class LockedException extends ConflictException {

	private static final long serialVersionUID = 185734899707722505L;

	public LockedException(String message) {
		super(message);
	}

}
