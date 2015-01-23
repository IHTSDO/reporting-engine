/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author mczotter
 * @since 1.0
 */
public class AlreadyExistsException extends ConflictException {

	private static final long serialVersionUID = 6347436684320140303L;

	public AlreadyExistsException(String type, String id) {
		super(formatMessage(type, id));
	}

	private static String formatMessage(String type, String id) {
		checkNotNull(type, "type");
		checkNotNull(id, "id");
		return String.format("%s with %s identifier already exists.", type, id);
	}
	
}
