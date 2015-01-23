/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.exception;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when details of a classification run can not be retrieved from the datastore.
 * 
 * @author Andras Peteri
 */
public class ClassificationRunNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public ClassificationRunNotFoundException(final String key) {
		super("Classification run", key);
	}
}
