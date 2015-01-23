/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.List;

import com.b2international.snowowl.rest.snomed.domain.ISnomedDescription;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedConceptDescriptions {

	private List<ISnomedDescription> conceptDescriptions;

	public List<ISnomedDescription> getConceptDescriptions() {
		return conceptDescriptions;
	}

	public void setConceptDescriptions(final List<ISnomedDescription> conceptDescriptions) {
		this.conceptDescriptions = conceptDescriptions;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedConceptDescriptions [conceptDescriptions=");
		builder.append(conceptDescriptions);
		builder.append("]");
		return builder.toString();
	}
}
