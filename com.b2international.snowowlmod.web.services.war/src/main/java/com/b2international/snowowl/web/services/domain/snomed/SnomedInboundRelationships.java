/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.List;

import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedInboundRelationships {

	private List<ISnomedRelationship> inboundRelationships;
	private int total;

	public List<ISnomedRelationship> getInboundRelationships() {
		return inboundRelationships;
	}

	public int getTotal() {
		return total;
	}

	public void setInboundRelationships(final List<ISnomedRelationship> inboundRelationships) {
		this.inboundRelationships = inboundRelationships;
	}

	public void setTotal(final int total) {
		this.total = total;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedInboundRelationships [inboundRelationships=");
		builder.append(inboundRelationships);
		builder.append(", total=");
		builder.append(total);
		builder.append("]");
		return builder.toString();
	}
}
