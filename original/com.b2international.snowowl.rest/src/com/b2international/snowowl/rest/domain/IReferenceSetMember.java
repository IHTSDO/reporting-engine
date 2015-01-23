/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

/**
 * Represents a terminology independent reference set member.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link #getReferenceSetId() <em>Get containing reference set identifier</em>}</li>
 *   <li>{@link #getReferencedComponentId() <em>Get referenced component identifier</em>}</li>
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface IReferenceSetMember extends IComponent {

	/**
	 * Returns the containing {@link IReferenceSet}'s component identifier.
	 * @return the component identifier of the containing reference set
	 */
	String getReferenceSetId();

	/**
	 * Returns the identifier of the referenced component.
	 * @return the identifier of the referenced component
	 */	
	String getReferencedComponentId();
}
