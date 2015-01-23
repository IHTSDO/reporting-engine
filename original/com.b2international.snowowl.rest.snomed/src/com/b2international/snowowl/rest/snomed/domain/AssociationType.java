/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.snomed.SnomedConstants.Concepts;

/**
 * TODO document
 * @author Andras Peteri
 */
public enum AssociationType {

	/**
	 * 
	 */
	ALTERNATIVE(Concepts.REFSET_ALTERNATIVE_ASSOCIATION),

	/**
	 * 
	 */
	MOVED_FROM(Concepts.REFSET_MOVED_FROM_ASSOCIATION),

	/**
	 * 
	 */
	MOVED_TO(Concepts.REFSET_MOVED_TO_ASSOCIATION),

	/**
	 * 
	 */
	POSSIBLY_EQUIVALENT_TO(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION),

	/**
	 * 
	 */
	REFERS_TO(Concepts.REFSET_REFERS_TO_ASSOCIATION),

	/**
	 * 
	 */
	REPLACED_BY(Concepts.REFSET_REPLACED_BY_ASSOCIATION),

	/**
	 * 
	 */
	SAME_AS(Concepts.REFSET_SAME_AS_ASSOCIATION),

	/**
	 * 
	 */
	SIMILAR_TO(Concepts.REFSET_SIMILAR_TO_ASSOCIATION),

	/**
	 * 
	 */
	WAS_A(Concepts.REFSET_WAS_A_ASSOCIATION);

	private final String conceptId;

	private AssociationType(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public static AssociationType getByConceptId(final String conceptId) {
		for (final AssociationType candidate : values()) {
			if (candidate.getConceptId().equals(conceptId)) {
				return candidate;
			}
		}

		return null;
	}
}
