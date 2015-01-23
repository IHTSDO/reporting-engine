/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

/**
 * Represents a terminology independent statement element with a source and a destination identifier.
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link #getSourceRef() <em>Get source identifier</em>}</li>
 *   <li>{@link #getDestinationRef() <em>Get destination identifier</em>}</li>
 * </ul>
 *
 * @author Andras Peteri
 */
public interface IComponentEdge {

	/**
	 * Returns the source component identifier.
	 * @return the source component identifier
	 */
	String getSourceId();

	/**
	 * Returns the destination component identifier.
	 * @return the destination component identifier
	 */
	String getDestinationId();
}
