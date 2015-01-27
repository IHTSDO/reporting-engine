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
public class SnomedOutboundRelationships {

	private List<ISnomedRelationship> outboundRelationships;
	private int total;

	public List<ISnomedRelationship> getOutboundRelationships() {
		return outboundRelationships;
	}

	public int getTotal() {
		return total;
	}

	public void setOutboundRelationships(final List<ISnomedRelationship> outboundRelationships) {
		this.outboundRelationships = outboundRelationships;
	}

	public void setTotal(final int total) {
		this.total = total;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedOutboundRelationships [outboundRelationships=");
		builder.append(outboundRelationships);
		builder.append(", total=");
		builder.append(total);
		builder.append("]");
		return builder.toString();
	}
}
