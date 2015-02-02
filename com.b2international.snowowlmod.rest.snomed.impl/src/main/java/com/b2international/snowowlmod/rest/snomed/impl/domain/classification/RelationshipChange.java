/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain.classification;

import com.b2international.snowowl.rest.snomed.domain.RelationshipModifier;
import com.b2international.snowowl.rest.snomed.domain.classification.ChangeNature;
import com.b2international.snowowl.rest.snomed.domain.classification.IRelationshipChange;

/**
 * @author apeteri
 */
public class RelationshipChange implements IRelationshipChange {

	private ChangeNature changeNature;
	private String sourceId;
	private String typeId;
	private String destinationId;
	private boolean destinationNegated;
	private int group;
	private int unionGroup;
	private RelationshipModifier modifier;

	@Override
	public ChangeNature getChangeNature() {
		return changeNature;
	}

	@Override
	public String getSourceId() {
		return sourceId;
	}

	@Override
	public String getTypeId() {
		return typeId;
	}

	@Override
	public String getDestinationId() {
		return destinationId;
	}

	@Override
	public boolean isDestinationNegated() {
		return destinationNegated;
	}

	@Override
	public int getGroup() {
		return group;
	}

	@Override
	public int getUnionGroup() {
		return unionGroup;
	}

	@Override
	public RelationshipModifier getModifier() {
		return modifier;
	}

	public void setChangeNature(final ChangeNature changeNature) {
		this.changeNature = changeNature;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public void setTypeId(final String typeId) {
		this.typeId = typeId;
	}

	public void setDestinationId(final String destinationId) {
		this.destinationId = destinationId;
	}

	public void setDestinationNegated(final boolean destinationNegated) {
		this.destinationNegated = destinationNegated;
	}

	public void setGroup(final int group) {
		this.group = group;
	}

	public void setUnionGroup(final int unionGroup) {
		this.unionGroup = unionGroup;
	}

	public void setModifier(final RelationshipModifier modifier) {
		this.modifier = modifier;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("RelationshipChange [changeNature=");
		builder.append(changeNature);
		builder.append(", sourceId=");
		builder.append(sourceId);
		builder.append(", typeId=");
		builder.append(typeId);
		builder.append(", destinationId=");
		builder.append(destinationId);
		builder.append(", destinationNegated=");
		builder.append(destinationNegated);
		builder.append(", group=");
		builder.append(group);
		builder.append(", unionGroup=");
		builder.append(unionGroup);
		builder.append(", modifier=");
		builder.append(modifier);
		builder.append("]");
		return builder.toString();
	}
}
