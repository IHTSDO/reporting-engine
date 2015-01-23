/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a terminology repository can not be found for a given repository unique identifier.
 * 
 * @author Andras Peteri
 */
public class RepositoryNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified repository unique identifier.
	 * 
	 * @param repositoryUuid the identifier of the repository which could not be found (may not be {@code null})
	 */
	public RepositoryNotFoundException(final String repositoryUuid) {
		super("Repository", repositoryUuid);
	}
}
