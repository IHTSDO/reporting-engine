/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

import com.b2international.commons.StringUtils;

/**
 * Enumerates high-level component categories which can exist in any code system.
 * 
 * @author Andras Peteri
 */
public enum ComponentCategory {

	/** 
	 * Represents a category for ideas, physical objects or events. 
	 */
	CONCEPT,

	/** 
	 * A label or other textual representation for a concept. 
	 */
	DESCRIPTION,

	/** 
	 * Represents a typed connection between two concepts. 
	 */
	RELATIONSHIP,

	/** 
	 * A scalar value or measurement associated with another component. 
	 */
	CONCRETE_DOMAIN,

	/** 
	 * A set of unique set members. 
	 */
	SET,

	/** 
	 * Points to another component, indicating that it is part of the member's parent set. 
	 */ 
	SET_MEMBER,

	/** 
	 * A set of unique map members. 
	 */
	MAP,

	/**
	 * Points to a source and a target component, indicating that a mapping exists between the two in the context of the
	 * member's parent map.
	 */
	MAP_MEMBER;

	/**
	 * TODO document
	 * @return
	 */
	public String getDisplayName() {
		return StringUtils.capitalizeFirstLetter(name().replace('_', ' ').toLowerCase());
	}
}
