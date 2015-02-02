/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain.classification;

import java.util.Collections;
import java.util.List;

import com.b2international.snowowl.rest.snomed.domain.classification.IRelationshipChange;
import com.b2international.snowowl.rest.snomed.domain.classification.IRelationshipChangeList;

/**
 * @author apeteri
 */
public class RelationshipChangeList implements IRelationshipChangeList {

	private List<IRelationshipChange> changes = Collections.emptyList();
	private int total;

	public List<IRelationshipChange> getChanges() {
		return changes;
	}

	public int getTotal() {
		return total;
	}

	public void setChanges(final List<IRelationshipChange> changes) {
		this.changes = changes;
	}

	public void setTotal(final int total) {
		this.total = total;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("RelationshipChangeList [changes=");
		builder.append(changes);
		builder.append(", total=");
		builder.append(total);
		builder.append("]");
		return builder.toString();
	}
}
