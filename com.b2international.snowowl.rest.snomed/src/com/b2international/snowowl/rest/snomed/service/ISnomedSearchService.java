/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.service;

import java.io.Serializable;

import com.b2international.snowowl.rest.service.ISearchService;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConceptSearchResult;
import com.b2international.snowowl.rest.snomed.domain.ISnomedSearchModel;

/**
 * TODO review documentation
 * 
 * SNOMED CT terminology dependent interface of the RESTful Search Service.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link ISnomedSearchService#getConcept(Serializable) <em>Retrieve concept</em>}</li>
 *   <li>{@link ISnomedSearchService#getConceptLabel(Serializable) <em>Retrieve concept label</em>}</li>
 *   <li>{@link ISnomedSearchService#search(boolean, boolean, boolean, boolean, boolean, String) <em>Search concept</em>}</li>
 *   <li>{@link ISnomedSearchService#search(boolean, boolean, boolean, boolean, boolean, String, String) <em>Search concept with filter</em>}</li>
 *   <li>{@link ISnomedSearchService#search(String) <em>Search concept based on the DOI</em>}</li>
 * </ul>
 * </p>
 * @param <K> type of the  SNOMED CT terminology dependent concept's unique identifier.
 * The &lt;<b>K</b>&gt; type should implement the {@link Serializable} interface.  
 * 
 * @author Akos Kitta
 * @author Akos Kitta - updating API for DOI based SNOMED&nbspCT concept search. (Since: 1.8)
 * @author Andras Peteri
 */
public interface ISnomedSearchService extends ISearchService<ISnomedSearchModel, ISnomedConceptSearchResult> {
	// Empty interface body
}
