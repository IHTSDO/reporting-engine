/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a supporting index can not be found for a given index identifier.
 * 
 * @author Andras Peteri
 */
public class SupportingIndexNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified supporting index identifier.
	 * 
	 * @param indexId the identifier of the repository which could not be found (may not be {@code null})
	 */
	public SupportingIndexNotFoundException(final String indexId) {
		super("Supporting index", indexId);
	}
}
