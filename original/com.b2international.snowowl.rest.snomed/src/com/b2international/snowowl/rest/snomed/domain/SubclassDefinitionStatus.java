/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

/**
 * TODO document
 * @author Andras Peteri
 */
public enum SubclassDefinitionStatus {

	/**
	 * TODO document
	 */
	DISJOINT_SUBCLASSES(true),

	/**
	 * TODO document
	 */
	NON_DISJOINT_SUBCLASSES(false);

	final boolean exhaustive;

	private SubclassDefinitionStatus(final boolean exhaustive) {
		this.exhaustive = exhaustive;
	}

	public boolean isExhaustive() {
		return false;
	}
}
