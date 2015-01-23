/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

import com.b2international.snowowl.rest.domain.ISearchModel;
import com.b2international.snowowl.rest.domain.ISearchResult;
import com.b2international.snowowl.rest.domain.quicksearch.IQuickSearchProviderResponse;

/**
 * Terminology independent interface of the Search Service.
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link #search(ISearchModel, int, int) <em>Search for components</em>}</li>
 *   <li>{@link #quickSearch(String, ISearchModel, int, String) <em>Retrieve component search suggestions</em>}</li>
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface ISearchService<M extends ISearchModel, R extends ISearchResult> {

	/**
	 * TODO document
	 * @param searchModel
	 * @param offset
	 * @param limit
	 * @return
	 */
	R search(ISearchModel searchModel, int offset, int limit);
	
	/**
	 * TODO document
	 * @param searchText
	 * @param quickSearchModel
	 * @param limit
	 * @param userId
	 * @return
	 */
	IQuickSearchProviderResponse quickSearch(String searchText, ISearchModel quickSearchModel, int limit, String userId);
}
