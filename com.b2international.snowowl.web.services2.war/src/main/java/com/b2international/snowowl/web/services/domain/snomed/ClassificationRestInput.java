/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

/**
 * @author apeteri
 */
public class ClassificationRestInput {

	private String reasonerId;

	public String getReasonerId() {
		return reasonerId;
	}

	public void setReasonerId(final String reasonerId) {
		this.reasonerId = reasonerId;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ClassificationRestInput [reasonerId=");
		builder.append(reasonerId);
		builder.append("]");
		return builder.toString();
	}
}
