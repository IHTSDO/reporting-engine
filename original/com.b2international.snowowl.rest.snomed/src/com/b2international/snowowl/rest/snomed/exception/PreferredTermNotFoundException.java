/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.exception;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when a preferred term can not be retrieved using the given language preferences.
 * 
 * @author Andras Peteri
 */
public class PreferredTermNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public PreferredTermNotFoundException(final String key) {
		super("Preferred term for concept", key);
	}
}
