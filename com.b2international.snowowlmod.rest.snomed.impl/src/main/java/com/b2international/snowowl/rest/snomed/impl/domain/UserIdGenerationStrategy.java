/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.IdGenerationStrategy;

/**
 * @author apeteri
 */
public class UserIdGenerationStrategy implements IdGenerationStrategy {

	private final String id;

	/**
	 * @param id
	 */
	public UserIdGenerationStrategy(final String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("UserIdGenerationStrategy [id=");
		builder.append(id);
		builder.append("]");
		return builder.toString();
	}
}
