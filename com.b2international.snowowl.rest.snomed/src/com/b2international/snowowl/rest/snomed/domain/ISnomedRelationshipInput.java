/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedRelationshipInput extends ISnomedComponentInput {

	/**
	 * TODO document
	 * @return
	 */
	String getSourceId();
	
	/**
	 * TODO document
	 * @return
	 */
	String getDestinationId();
	
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
	RelationshipModifier getModifier();
}
