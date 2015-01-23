/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

import com.b2international.snowowl.rest.domain.ComponentCategory;

/**
 * Thrown when a terminology component can not be found for a given component identifier.
 * 
 * @author Andras Peteri
 */
public class ComponentNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public ComponentNotFoundException(final ComponentCategory category, final String key) {
		super(category.getDisplayName(), key);
	}
}
