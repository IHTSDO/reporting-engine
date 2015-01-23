/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.domain.quicksearch;

/**
 * Represents a matching text region within a {@link IQuickSearchProviderResponseEntry quick search result entry}.
 * 
 * @author zstorok
 */
public interface IQuickSearchMatchRegion {
	/**
	 * Returns the start index of the matching region.
	 * 
	 * @return the start index of the matching region
	 */
	int getStart();
	
	/**
	 * Returns the end index of the matching region.
	 * 
	 * @return the end index of the matching region
	 */
	int getEnd();
}
