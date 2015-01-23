/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.codesystem;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a version within a code system can not be found for a given version identifier.
 * 
 * @author Andras Peteri
 */
public class CodeSystemVersionNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified code system version identifier.
	 * 
	 * @param version the identifier of the version which could not be found (may not be {@code null})
	 */
	public CodeSystemVersionNotFoundException(final String version) {
		super("Code system version", version);
	}
}
