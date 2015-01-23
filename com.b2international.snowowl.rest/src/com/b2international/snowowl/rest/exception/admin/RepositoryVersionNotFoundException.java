/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a version within a repository can not be found for a given version identifier.
 * 
 * @author Andras Peteri
 */
public class RepositoryVersionNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified repository version identifier.
	 * 
	 * @param repositoryVersionId the identifier of the version which could not be found (may not be {@code null})
	 */
	public RepositoryVersionNotFoundException(final String repositoryVersionId) {
		super("Repository version", repositoryVersionId);
	}
}
