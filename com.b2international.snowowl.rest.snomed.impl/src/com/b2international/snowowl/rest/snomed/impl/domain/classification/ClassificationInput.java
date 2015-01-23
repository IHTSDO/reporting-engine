/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.domain.classification;

import com.b2international.snowowl.rest.snomed.domain.classification.IClassificationInput;

/**
 * @author apeteri
 */
public class ClassificationInput implements IClassificationInput {

	private String reasonerId;
	private String userId;

	@Override
	public String getReasonerId() {
		return reasonerId;
	}

	@Override
	public String getUserId() {
		return userId;
	}

	public void setReasonerId(final String reasonerId) {
		this.reasonerId = reasonerId;
	}

	public void setUserId(final String userId) {
		this.userId = userId;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ClassificationInput [reasonerId=");
		builder.append(reasonerId);
		builder.append(", userId=");
		builder.append(userId);
		builder.append("]");
		return builder.toString();
	}
}
