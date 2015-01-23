/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.b2international.snowowl.snomed.datastore.SnomedInactivationPlan.InactivationReason;

/**
 * TODO document
 * @author Andras Peteri
 */
public enum InactivationIndicator {

	/**
	 * 
	 */
	RETIRED(InactivationReason.RETIRED),

	/**
	 * 
	 */
	AMBIGUOUS(InactivationReason.AMBIGUOUS),

	/**
	 * 
	 */
	DUPLICATE(InactivationReason.DUPLICATE),

	/**
	 * 
	 */
	ERRONEOUS(InactivationReason.ERRONEOUS),

	/**
	 * 
	 */
	MOVED_ELSEWHERE(InactivationReason.MOVED_TO);

	private final InactivationReason inactivationReason;

	private InactivationIndicator(final InactivationReason inactivationReason) {
		this.inactivationReason = inactivationReason;
	}

	public String getConceptId() {
		return inactivationReason.getInactivationReasonConceptId();
	}

	public static InactivationIndicator getByConceptId(final String conceptId) {
		checkNotNull(conceptId, "Concept identifier may not be null.");
		// XXX: Avoid matching on RETIRED by concept identifier 
		checkArgument(!conceptId.isEmpty(), "Concept identifier may not be empty."); 

		for (final InactivationIndicator candidate : values()) {
			if (candidate.getConceptId().equals(conceptId)) {
				return candidate;
			}
		}

		return RETIRED;
	}

	public InactivationReason toInactivationReason() {
		return inactivationReason;
	}
}
