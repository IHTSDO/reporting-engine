package com.b2international.snowowl.rest.domain.quicksearch;

import java.util.List;

/**
 * Represents the response of the quick search service.
 *  
 * @author zstorok
 */
public interface IQuickSearchResponse {

	/**
	 * Returns the responses grouped by their quick search provider.
	 * 
	 * @return the responses grouped by their quick search provider
	 */
	List<IQuickSearchProviderResponse> getProviderResponses();

	/**
	 * Returns the suggested suffix.
	 * 
	 * @return the suggested suffix
	 */
	String getSuggestedSuffix();

	/**
	 * Returns the total number of approximate matches.
	 * 
	 * @return the total number of approximate matches
	 */
	int getTotalApproximateMatchCount();

	/**
	 * Returns the total number of exact matches.
	 * @return the total number of exact matches
	 */
	int getTotalExactMatchCount();

}