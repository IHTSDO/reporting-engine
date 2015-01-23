/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.rest.domain.IComponentEdge;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedRelationship extends ISnomedComponent, IComponentEdge {

	/**
	 * TODO document
	 * @return
	 */
	boolean isDestinationNegated();

	/**
	 * TODO document
	 * @return
	 */
	String getTypeId();

	/**
	 * TODO document
	 * @return
	 */
	int getGroup();

	/**
	 * TODO document
	 * @return
	 */
	int getUnionGroup();

	/**
	 * TODO document
	 * @return
	 */
	CharacteristicType getCharacteristicType();

	/**
	 * TODO document
	 * @return
	 */
	RelationshipRefinability getRefinability();

	/**
	 * TODO document
	 * @return
	 */
	RelationshipModifier getModifier();
}
