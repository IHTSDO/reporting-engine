package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.exception.NotFoundException;

public class LexicalModelNotFoundException extends NotFoundException {
	/**
	 * Creates a new instance with the specified type and key.
	 *
	 * @param key  the unique key of the item which was not found (may not be {@code null})
	 */
	protected LexicalModelNotFoundException(String key) {
		super("Lexical Model", key);
	}
}
