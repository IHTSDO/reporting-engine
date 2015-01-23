/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

/**
 * @author mczotter
 * @since 1.0
 */
public class ConflictException extends RuntimeException {

	private static final long serialVersionUID = -2887608541911973086L;
	
	public ConflictException(String message) {
		super(message);
	}

}
