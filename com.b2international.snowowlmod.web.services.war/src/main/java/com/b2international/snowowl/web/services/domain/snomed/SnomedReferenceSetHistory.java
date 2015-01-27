/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.List;

import com.b2international.snowowl.rest.domain.history.IHistoryInfo;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedReferenceSetHistory {

	private List<IHistoryInfo> referenceSetHistory;

	public List<IHistoryInfo> getReferenceSetHistory() {
		return referenceSetHistory;
	}

	public void setReferenceSetHistory(final List<IHistoryInfo> referenceSetHistory) {
		this.referenceSetHistory = referenceSetHistory;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedReferenceSetHistory [referenceSetHistory=");
		builder.append(referenceSetHistory);
		builder.append("]");
		return builder.toString();
	}
}
