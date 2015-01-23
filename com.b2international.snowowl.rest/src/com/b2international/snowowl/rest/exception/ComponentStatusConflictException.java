/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception;

/**
 * Specific {@link StoreException} denoting that a component has been already inactivated in a previous request.
 * 
 * @author Mark Czotter
 * @author Andras Peteri
 * @since 1.0
 */
public class ComponentStatusConflictException extends ConflictException {

	private static final long serialVersionUID = -8674961206384074905L;

	public ComponentStatusConflictException(String componentId, boolean componentStatus) {
		super(String.format("Component %s is already %s.", componentId, componentStatus ? "active" : "inactive"));
	}

}
