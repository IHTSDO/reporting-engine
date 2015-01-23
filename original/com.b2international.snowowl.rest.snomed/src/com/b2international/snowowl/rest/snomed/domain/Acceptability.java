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
public enum Acceptability {

	/**
	 * TODO document
	 */
	ACCEPTABLE(Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_ACCEPTABLE),

	/**
	 * TODO document
	 */
	PREFERRED(Concepts.REFSET_DESCRIPTION_ACCEPTABILITY_PREFERRED);

	private final String conceptId;

	private Acceptability(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public static Acceptability getByConceptId(final String conceptId) {
		for (final Acceptability candidate : values()) {
			if (candidate.getConceptId().equals(conceptId)) {
				return candidate;
			}
		}

		throw new IllegalArgumentException(MessageFormat.format("No acceptability value found for identifier ''{0}''.", conceptId));
	}
}
