/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

/**
 * Represents a terminology independent reference set.
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link #getReferencedComponentCategory() <em>Referenced component category</em>}</li>
 *   <li>{@link #getMemberCount() <em>Number of contained members</em>}</li>
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface IReferenceSet extends IComponent {

	/**
	 * Returns the category of the referenced component.
	 * @return the category of the referenced component
	 */
	ComponentCategory getReferencedComponentCategory();
	
	/**
	 * Returns the member count for this reference set.
	 * @return the total number of reference set members
	 */
	int getMemberCount();
}
