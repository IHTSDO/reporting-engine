/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.MessageFormat;

/**
 * Thrown when a requested item could not be found.
 * 
 * @author Andras Peteri
 */
public abstract class NotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private static String getMessage(final String type, final String key) {
		checkNotNull(type, "Item type may not be null.");
		checkNotNull(key, "Item key may not be null.");
		return MessageFormat.format("{0} with identifier {1} could not be found.", type, key);
	}

	private final String type;
	private final String key;

	/**
	 * Creates a new instance with the specified type and key.
	 * 
	 * @param type the type of the item which was not found (may not be {@code null})
	 * @param key the unique key of the item which was not found (may not be {@code null})
	 */
	protected NotFoundException(final String type, final String key) {
		super(getMessage(type, key));
		this.type = type;
		this.key = key;
	}

	/**
	 * @return the type of the missing item
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the unique key of the missing item
	 */
	public String getKey() {
		return key;
	}
}
