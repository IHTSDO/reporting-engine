/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.CharacteristicType;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;
import com.b2international.snowowl.rest.snomed.domain.RelationshipModifier;
import com.b2international.snowowl.rest.snomed.domain.RelationshipRefinability;

/**
 * Represents a SNOMEd&nbsp;CT relationship.
 * 
 * @author akitta
 * @author apeteri
 */
public class SnomedRelationship extends AbstractSnomedComponent implements ISnomedRelationship {

	private String sourceId;
	private String destinationId;
	private boolean destinationNegated;
	private String typeId;
	private int group;
	private int unionGroup;
	private CharacteristicType characteristicType;
	private RelationshipRefinability refinability;
	private RelationshipModifier modifier;

	@Override
	public String getSourceId() {
		return sourceId;
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
	public String getTypeId() {
		return typeId;
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
	public CharacteristicType getCharacteristicType() {
		return characteristicType;
	}

	@Override
	public RelationshipRefinability getRefinability() {
		return refinability;
	}

	@Override
	public RelationshipModifier getModifier() {
		return modifier;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public void setDestinationId(final String destinationId) {
		this.destinationId = destinationId;
	}

	public void setDestinationNegated(final boolean destinationNegated) {
		this.destinationNegated = destinationNegated;
	}

	public void setTypeId(final String typeId) {
		this.typeId = typeId;
	}

	public void setGroup(final int group) {
		this.group = group;
	}

	public void setUnionGroup(final int unionGroup) {
		this.unionGroup = unionGroup;
	}

	public void setCharacteristicType(final CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
	}

	public void setRefinability(final RelationshipRefinability refinability) {
		this.refinability = refinability;
	}

	public void setModifier(final RelationshipModifier modifier) {
		this.modifier = modifier;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedRelationship [getId()=");
		builder.append(getId());
		builder.append(", isReleased()=");
		builder.append(isReleased());
		builder.append(", isActive()=");
		builder.append(isActive());
		builder.append(", getEffectiveTime()=");
		builder.append(getEffectiveTime());
		builder.append(", getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getSourceId()=");
		builder.append(getSourceId());
		builder.append(", getDestinationId()=");
		builder.append(getDestinationId());
		builder.append(", isDestinationNegated()=");
		builder.append(isDestinationNegated());
		builder.append(", getTypeId()=");
		builder.append(getTypeId());
		builder.append(", getGroup()=");
		builder.append(getGroup());
		builder.append(", getUnionGroup()=");
		builder.append(getUnionGroup());
		builder.append(", getCharacteristicType()=");
		builder.append(getCharacteristicType());
		builder.append(", getRefinability()=");
		builder.append(getRefinability());
		builder.append(", getModifier()=");
		builder.append(getModifier());
		builder.append("]");
		return builder.toString();
	}
}
