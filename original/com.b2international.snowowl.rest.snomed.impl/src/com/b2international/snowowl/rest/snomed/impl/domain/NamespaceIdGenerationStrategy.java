/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.domain;

import java.text.MessageFormat;

import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.snomed.domain.IdGenerationStrategy;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext.ComponentNature;
import com.b2international.snowowl.snomed.datastore.id.SnomedIdentifiers;

/**
 * @author apeteri
 */
public class NamespaceIdGenerationStrategy implements IdGenerationStrategy {

	private static ComponentNature toComponentNature(final ComponentCategory category) {
		switch (category) {
			case CONCEPT:
				return ComponentNature.CONCEPT;
			case DESCRIPTION:
				return ComponentNature.DESCRIPTION;
			case RELATIONSHIP:
				return ComponentNature.RELATIONSHIP;
			default:
				throw new UnsupportedOperationException(MessageFormat.format("Unsupported category: ''{0}''.", category));
		}
	}

	private final ComponentCategory category;
	private final String namespaceId;

	/**
	 * @param category
	 * @param namespaceId
	 */
	public NamespaceIdGenerationStrategy(final ComponentCategory category, final String namespaceId) {
		this.category = category;
		this.namespaceId = namespaceId;
	}

	@Override
	public String getId() {
		return SnomedIdentifiers.generateComponentId(namespaceId, toComponentNature(category));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("NamespaceIdGenerationStrategy [category=");
		builder.append(category);
		builder.append(", namespaceId=");
		builder.append(namespaceId);
		builder.append("]");
		return builder.toString();
	}
}
