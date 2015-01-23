/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.codesystem;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a code system can not be found for the given short name.
 * 
 * @author Andras Peteri
 */
public class CodeSystemNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified short name.
	 * 
	 * @param shortName the short name of the code system which could not be found (may not be {@code null})
	 */
	public CodeSystemNotFoundException(final String shortName) {
		super("Code system", shortName);
	}
}
