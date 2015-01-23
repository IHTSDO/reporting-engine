/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.snomed.SnomedConstants.Concepts;

/**
 * TODO document
 * @author Andras Peteri
 */
public enum DefinitionStatus {

	/**
	 * TODO document
	 */
	PRIMITIVE(Concepts.PRIMITIVE),

	/**
	 * TODO document
	 */
	FULLY_DEFINED(Concepts.FULLY_DEFINED);

	private final String conceptId;
	
	private DefinitionStatus(final String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}
}
