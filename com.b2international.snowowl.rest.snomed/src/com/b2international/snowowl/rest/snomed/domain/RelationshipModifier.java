/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.snomed.SnomedConstants.Concepts;

/**
 * TODO document
 * @author Andras Peteri
 */
public enum RelationshipModifier {

	/**
	 * TODO document
	 */
	EXISTENTIAL(Concepts.EXISTENTIAL_RESTRICTION_MODIFIER),

	/**
	 * TODO document
	 */
	UNIVERSAL(Concepts.UNIVERSAL_RESTRICTION_MODIFIER);

	private final String conceptId;

	private RelationshipModifier(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}
}
