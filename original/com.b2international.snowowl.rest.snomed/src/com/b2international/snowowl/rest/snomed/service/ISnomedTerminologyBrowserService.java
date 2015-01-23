/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.service;

import java.io.Serializable;

import com.b2international.snowowl.rest.service.IComponentNodeService;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConcept;

/**
 * TODO review javadoc
 * 
 * SNOMED CT specific interface of the RESTful Terminology Browser Service.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link ISnomedClientTerminologyBrowserService#getRootConcepts() <em>Retrieve root concepts</em>}</li>
 *   <li>{@link ISnomedClientTerminologyBrowserService#getSuperTypes(Serializable, boolean) <em>Retrieve super concepts</em>}</li>
 *   <li>{@link ISnomedClientTerminologyBrowserService#getSubTypes(Serializable, boolean) <em>Retrieve sub concepts</em>}</li>
 * </ul>
 * </p>
 * @param <T> type of the SNOMED CT concept. 
 * The &lt;<b>T</b>&gt; type should implement the {@link IContainerElement} interface.
 * @param <K> type of the SNOMED CT concept's unique identifier.
 * The &lt;<b>K</b>&gt; type should implement the {@link Serializable} interface.  
 * 
 * @author Akos Kitta
 * @author Andras Peteri
 */
public interface ISnomedTerminologyBrowserService extends IComponentNodeService<ISnomedConcept> {
	// Empty interface body
}
