/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

/**
 * Thrown when the supplied query parameters are not acceptable.
 *
 * @author Andras Peteri
 */
public class IllegalQueryParameterException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	public IllegalQueryParameterException(final String message) {
		super(message);
	}

}
