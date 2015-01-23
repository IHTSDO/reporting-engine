/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.domain.quicksearch;

import java.util.List;

/**
 * Represents a quick search result entry.
 * 
 * @author zstorok
 */
public interface IQuickSearchProviderResponseEntry {

	/**
	 * Returns the identifier of the entry.
	 * 
	 * @return the identifier
	 */
	String getId();
	
	/**
	 * Returns the label of the entry.
	 * 
	 * @return the label
	 */
	String getLabel();
	
	/**
	 * Returns <code>true</code> if the result entry represents an approximate match, <code>false</code> otherwise.
	 * 
	 * @return true if the result entry represents an approximate match, false otherwise
	 */
	boolean isApproximate();
	
	/**
	 * Returns the URL of the image associated with the result entry.
	 * 
	 * @return the URL of the associated image
	 */
	String getImageUrl();
	
	/**
	 * Returns the {@link IQuickSearchMatchRegion match regions} within the result entry.
	 * 
	 * @return the match regions
	 */
	List<IQuickSearchMatchRegion> getMatchRegions();

}