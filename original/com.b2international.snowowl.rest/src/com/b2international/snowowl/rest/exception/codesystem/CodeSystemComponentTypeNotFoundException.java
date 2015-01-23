/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.codesystem;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a component type within a code system can not be found for a given type identifier.
 * 
 * @author Andras Peteri
 */
public class CodeSystemComponentTypeNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified code system component type identifier.
	 * 
	 * @param typeId the identifier of the component type which could not be found (may not be {@code null})
	 */
	public CodeSystemComponentTypeNotFoundException(final String typeId) {
		super("Code system component type", typeId);
	}
}
