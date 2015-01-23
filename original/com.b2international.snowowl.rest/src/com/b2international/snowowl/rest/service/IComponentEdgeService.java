/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

import com.b2international.snowowl.rest.domain.IComponentEdge;
import com.b2international.snowowl.rest.domain.IComponentList;
import com.b2international.snowowl.rest.domain.IComponentRef;

/**
 * Terminology independent interface of the Component Edge Service.
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link #getInboundEdges(IComponentRef) <em>Retrieve inbound edges</em>}</li>
 *   <li>{@link #getOutboundEdges(IComponentRef) <em>Retrieve outbound edges</em>}</li>
 * </ul>
 * 
 * @param <E> the concrete component edge type (must implement {@link IComponentEdge}
 * 
 * @author Andras Peteri
 */
public interface IComponentEdgeService<E extends IComponentEdge> {

	/**
	 * Retrieves a segment of the list of inbound edges for the specified component reference.
	 * @param nodeRef the component reference to look for (may not be {@code null})
	 * @param offset the starting offset in the list (may not be negative)
	 * @param limit the maximum number of results to return (may not be negative)
	 * @return the list of inbound edges for the component, sorted by source component reference (never {@code null}) 
	 */
	IComponentList<E> getInboundEdges(IComponentRef nodeRef, int offset, int limit);

	/**
	 * Retrieves a segment of the list of outbound edges for the specified component reference.
	 * @param nodeRef the component reference to look for (may not be {@code null})
	 * @param offset the starting offset in the list (may not be negative)
	 * @param limit the maximum number of results to return (may not be negative)
	 * @return the list of outbound edges for the component, sorted by target component reference (never {@code null}) 
	 */
	IComponentList<E> getOutboundEdges(IComponentRef nodeRef, int offset, int limit);
}
