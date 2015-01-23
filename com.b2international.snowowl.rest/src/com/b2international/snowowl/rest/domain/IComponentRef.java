/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

/**
 * Points to an identifiable, versioned component in a code system, on a particular task.
 * 
 * @author Andras Peteri
 */
public interface IComponentRef extends Comparable<IComponentRef>, IStorageRef {

	/**
	 * Returns the identifier of the component, eg. "{@code 116680003}".
	 * @return the component identifier
	 */
	String getComponentId();
}
