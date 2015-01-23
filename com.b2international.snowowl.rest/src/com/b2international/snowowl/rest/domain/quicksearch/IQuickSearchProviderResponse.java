/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.domain.quicksearch;

import java.util.List;

/**
 * Represents the response from a single quick search provider.
 * 
 * @author zstorok
 */
public interface IQuickSearchProviderResponse {

	/**
	 * Returns the name of the quick search provider.
	 * 
	 * @return the name of the quick search provider
	 */
	String getProviderName();
	
	/**
	 * Returns the total number of matches for the last expression received via {@link #filterElements(String)}.
	 * 
	 * @return the number of matches
	 */
	int getTotalHitCount();

	/**
	 * Returns the {@link IQuickSearchProviderResponseEntry quick search result entries} found by this provider.
	 * 
	 * @return the quick search result entries
	 */
	List<IQuickSearchProviderResponseEntry> getEntries();
	
}