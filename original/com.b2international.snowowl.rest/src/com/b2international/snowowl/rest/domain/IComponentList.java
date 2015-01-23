/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

import java.util.List;

/**
 * TODO document
 * 
 * @param <C> the component type
 * @author Andras Peteri
 */
public interface IComponentList<C> {

	/**
	 * TODO document
	 * @return
	 */
	int getTotalMembers();

	/**
	 * TODO document
	 * @return
	 */
	List<C> getMembers();
}
