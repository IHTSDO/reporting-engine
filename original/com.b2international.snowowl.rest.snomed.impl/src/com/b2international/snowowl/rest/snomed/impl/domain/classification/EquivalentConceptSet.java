/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.domain.classification;

import java.util.List;

import com.b2international.snowowl.rest.snomed.domain.classification.IEquivalentConcept;
import com.b2international.snowowl.rest.snomed.domain.classification.IEquivalentConceptSet;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author apeteri
 */
public class EquivalentConceptSet implements IEquivalentConceptSet {

	private boolean unsatisfiable;
	private List<IEquivalentConcept> equivalentConcepts;

	@Override
	public boolean isUnsatisfiable() {
		return unsatisfiable;
	}

	@Override
	public List<IEquivalentConcept> getEquivalentConcepts() {
		return equivalentConcepts;
	}

	public void setUnsatisfiable(final boolean unsatisfiable) {
		this.unsatisfiable = unsatisfiable;
	}

	@JsonDeserialize(contentAs=EquivalentConcept.class)
	public void setEquivalentConcepts(final List<IEquivalentConcept> equivalentConcepts) {
		this.equivalentConcepts = equivalentConcepts;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("EquivalentConceptSet [unsatisfiable=");
		builder.append(unsatisfiable);
		builder.append(", equivalentConcepts=");
		builder.append(equivalentConcepts);
		builder.append("]");
		return builder.toString();
	}
}
