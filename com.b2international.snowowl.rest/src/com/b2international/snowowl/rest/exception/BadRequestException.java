/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

/**
 * @author mczotter
 * @since 1.0
 */
public class BadRequestException extends RuntimeException {

	private static final long serialVersionUID = 7998450893448621719L;

	public BadRequestException(String message, Object...args) {
		super(String.format(message, args));
	}
	
}
