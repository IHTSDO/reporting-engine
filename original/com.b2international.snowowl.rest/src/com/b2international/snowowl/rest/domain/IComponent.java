/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

/**
 * Represents an identifiable component instance of a code system.
 * 
 * @author Andras Peteri
 */
public interface IComponent {

	/**
	 * Returns the component identifier.
	 * @return the component identifier
	 */
	String getId();
	
	/**
	 * Checks the component's release status.
	 * @return {@code true} if the component has already been released as part of a version (and so it is discouraged to
	 * delete it in later versions), {@code false} otherwise
	 */
	boolean isReleased();
}
