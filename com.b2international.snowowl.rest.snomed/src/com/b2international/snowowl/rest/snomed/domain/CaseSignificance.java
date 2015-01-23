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
public enum CaseSignificance {

	/**
	 * TODO document 
	 */
	ENTIRE_TERM_CASE_SENSITIVE(Concepts.ENTIRE_TERM_CASE_SENSITIVE),

	/**
	 * TODO document 
	 */
	CASE_INSENSITIVE(Concepts.ENTIRE_TERM_CASE_INSENSITIVE),

	/**
	 * TODO document
	 */
	INITIAL_CHARACTER_CASE_INSENSITIVE(Concepts.ONLY_INITIAL_CHARACTER_CASE_INSENSITIVE);

	private final String conceptId;

	private CaseSignificance(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public static CaseSignificance getByConceptId(final String conceptId) {
		for (final CaseSignificance candidate : values()) {
			if (candidate.getConceptId().equals(conceptId)) {
				return candidate;
			}
		}

		throw new IllegalArgumentException(MessageFormat.format("No case significance value found for identifier ''{0}''.", conceptId));
	}
}
