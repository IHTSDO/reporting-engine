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
public enum CharacteristicType {

	/**
	 * TODO document
	 */
	DEFINING_RELATIONSHIP(Concepts.DEFINING_RELATIONSHIP),

	/**
	 * TODO document
	 */
	STATED_RELATIONSHIP(Concepts.STATED_RELATIONSHIP),

	/**
	 * TODO document
	 */
	INFERRED_RELATIONSHIP(Concepts.INFERRED_RELATIONSHIP),

	/**
	 * TODO document
	 */
	QUALIFYING_RELATIONSHIP(Concepts.QUALIFYING_RELATIONSHIP),

	/**
	 * TODO document
	 */
	ADDITIONAL_RELATIONSHIP(Concepts.ADDITIONAL_RELATIONSHIP);

	private final String conceptId;

	private CharacteristicType(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public static CharacteristicType getByConceptId(final String conceptId) {
		for (final CharacteristicType candidate : values()) {
			if (candidate.getConceptId().equals(conceptId)) {
				return candidate;
			}
		}

		throw new IllegalArgumentException(MessageFormat.format("No characteristic type value found for identifier ''{0}''.", conceptId));
	}
}
