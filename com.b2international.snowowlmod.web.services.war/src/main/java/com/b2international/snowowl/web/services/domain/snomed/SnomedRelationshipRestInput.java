/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.snomed.domain.CharacteristicType;
import com.b2international.snowowl.rest.snomed.domain.RelationshipModifier;
import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedRelationshipInput;

import static com.b2international.snowowl.rest.domain.ComponentCategory.RELATIONSHIP;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedRelationshipRestInput extends AbstractSnomedComponentRestInput<SnomedRelationshipInput> {

	private CharacteristicType characteristicType = CharacteristicType.STATED_RELATIONSHIP;
	private String destinationId;
	private boolean destinationNegated = false;
	private int group = 0;
	private RelationshipModifier modifier = RelationshipModifier.EXISTENTIAL;
	private String sourceId;
	private String typeId;
	private int unionGroup = 0;

	public CharacteristicType getCharacteristicType() {
		return characteristicType;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public boolean isDestinationNegated() {
		return destinationNegated;
	}

	public int getGroup() {
		return group;
	}

	public RelationshipModifier getModifier() {
		return modifier;
	}

	public String getSourceId() {
		return sourceId;
	}

	public String getTypeId() {
		return typeId;
	}

	public int getUnionGroup() {
		return unionGroup;
	}

	public void setCharacteristicType(final CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
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

	public void setModifier(final RelationshipModifier modifier) {
		this.modifier = modifier;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public void setTypeId(final String typeId) {
		this.typeId = typeId;
	}

	public void setUnionGroup(final int unionGroup) {
		this.unionGroup = unionGroup;
	}

	@Override
	protected SnomedRelationshipInput createComponentInput() {
		return new SnomedRelationshipInput();
	}

	/**
	 * @return
	 */
	@Override
	public SnomedRelationshipInput toComponentInput(final String version, final String taskId) {
		final SnomedRelationshipInput result = super.toComponentInput(version, taskId);

		result.setCharacteristicType(getCharacteristicType());
		result.setDestinationId(getDestinationId());
		result.setDestinationNegated(isDestinationNegated());
		result.setGroup(getGroup());
		result.setModifier(getModifier());
		result.setSourceId(getSourceId());
		result.setTypeId(getTypeId());
		result.setUnionGroup(getUnionGroup());

		return result;
	}

	@Override
	protected ComponentCategory getComponentCategory() {
		return RELATIONSHIP;
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedRelationshipRestInput [getId()=");
		builder.append(getId());
		builder.append(", getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getCharacteristicType()=");
		builder.append(getCharacteristicType());
		builder.append(", getDestinationId()=");
		builder.append(getDestinationId());
		builder.append(", isDestinationNegated()=");
		builder.append(isDestinationNegated());
		builder.append(", getGroup()=");
		builder.append(getGroup());
		builder.append(", getModifier()=");
		builder.append(getModifier());
		builder.append(", getSourceId()=");
		builder.append(getSourceId());
		builder.append(", getTypeId()=");
		builder.append(getTypeId());
		builder.append(", getUnionGroup()=");
		builder.append(getUnionGroup());
		builder.append(", createComponentInput()=");
		builder.append(createComponentInput());
		builder.append("]");
		return builder.toString();
	}
}
