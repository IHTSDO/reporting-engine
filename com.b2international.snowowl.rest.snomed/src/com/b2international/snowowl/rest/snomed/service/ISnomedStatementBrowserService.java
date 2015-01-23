/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.service;

import java.io.Serializable;

import com.b2international.snowowl.rest.service.IComponentEdgeService;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;

/**
 * TODO review javadoc
 * 
 * SNOMED CT specific interface of the RESTful Statement Browser Service.
 *
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link ISnomedClientStatementBrowserService#getInboundStatements(Serializable) <em>Retrieve inbound statements</em>}</li>
 *   <li>{@link ISnomedClientStatementBrowserService#getOutboundStatements(Serializable) <em>Retrieve outbound statements</em>}</li>
 * </ul>
 * </p>
 * 
 * @author Akos Kitta
 * @author Andras Peteri
 */
public interface ISnomedStatementBrowserService extends IComponentEdgeService<ISnomedRelationship> {
	// Empty interface body
}
