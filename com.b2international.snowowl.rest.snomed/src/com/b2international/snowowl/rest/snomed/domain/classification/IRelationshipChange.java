/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.classification;

import com.b2international.snowowl.rest.snomed.domain.RelationshipModifier;

/**
 * TODO
 * @author Andras Peteri
 */
public interface IRelationshipChange {

	/**
	 * 
	 * @return
	 */
	ChangeNature getChangeNature();

	/**
	 * 
	 * @return
	 */
	String getSourceId();

	/**
	 * 
	 * @return
	 */
	String getTypeId();

	/**
	 * 
	 * @return
	 */
	String getDestinationId();

	/**
	 * 
	 * @return
	 */
	boolean isDestinationNegated();

	/**
	 * 
	 * @return
	 */
	int getGroup();

	/**
	 * 
	 * @return
	 */
	int getUnionGroup();

	/**
	 * 
	 * @return
	 */
	RelationshipModifier getModifier();
}
