/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

import java.util.List;

import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.domain.history.IHistoryInfo;
import com.b2international.snowowl.rest.exception.ComponentNotFoundException;

/**
 * Terminology independent interface of the History Service.
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link #getHistory(IComponentRef) <em>Retrieve history information</em>}</li>
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface IHistoryService {

	/**
	 * Returns information about all historical modifications made on the specified component.
	 * @param ref the reference pointing to the component (may not be {@code null})
	 * @return an object wrapping historical information, describing all changes made to the component
	 * @throws ComponentNotFoundException if the component reference can not be resolved
	 */
	List<IHistoryInfo> getHistory(final IComponentRef ref);
}
