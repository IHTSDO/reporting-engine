/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import com.b2international.snowowl.rest.domain.IComponentEdge;

/**
 * @author apeteri
 */
public abstract class AbstractComponentEdge implements IComponentEdge {

	private String sourceId;
	private String destinationId;

	@Override
	public String getSourceId() {
		return sourceId;
	}

	@Override
	public String getDestinationId() {
		return destinationId;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public void setDestinationId(final String destinationId) {
		this.destinationId = destinationId;
	}
}
