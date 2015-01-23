/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import java.text.MessageFormat;

import com.b2international.snowowl.snomed.SnomedConstants.Concepts;

/**
 * TODO document
 * @author Andras Peteri
 */
public enum RelationshipRefinability {

	/**
	 * TODO document
	 */
	NOT_REFINABLE(Concepts.NOT_REFINABLE),

	/**
	 * TODO document
	 */
	OPTIONAL(Concepts.OPTIONAL_REFINABLE),

	/**
	 * TODO document
	 */
	MANDATORY(Concepts.MANDATORY_REFINABLE);

	private final String conceptId;

	private RelationshipRefinability(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public static RelationshipRefinability getByConceptId(final String conceptId) {
		for (final RelationshipRefinability candidate : values()) {
			if (candidate.getConceptId().equals(conceptId)) {
				return candidate;
			}
		}

		throw new IllegalArgumentException(MessageFormat.format("No relationship refinability value found for identifier ''{0}''.", conceptId));
	}
}
