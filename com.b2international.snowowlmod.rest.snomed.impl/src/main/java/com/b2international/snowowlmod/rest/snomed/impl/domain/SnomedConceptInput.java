/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import java.util.Collections;
import java.util.List;

import com.b2international.snowowl.rest.snomed.domain.ISnomedConceptInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescriptionInput;
import com.b2international.snowowl.rest.snomed.domain.IdGenerationStrategy;
import com.google.common.collect.ImmutableList;

/**
 * @author apeteri
 */
public class SnomedConceptInput extends AbstractSnomedComponentInput implements ISnomedConceptInput {

	private List<ISnomedDescriptionInput> descriptions = Collections.emptyList();
	private String parentId;
	private IdGenerationStrategy isAIdGenerationStrategy;

	@Override
	public List<ISnomedDescriptionInput> getDescriptions() {
		return descriptions;
	}

	@Override
	public String getParentId() {
		return parentId;
	}

	@Override
	public IdGenerationStrategy getIsAIdGenerationStrategy() {
		return isAIdGenerationStrategy;
	}

	public void setParentId(final String parentId) {
		this.parentId = parentId;
	}

	public void setIsAIdGenerationStrategy(final IdGenerationStrategy isAIdGenerationStrategy) {
		this.isAIdGenerationStrategy = isAIdGenerationStrategy;
	}

	public void setDescriptions(final List<? extends ISnomedDescriptionInput> descriptions) {
		this.descriptions = ImmutableList.copyOf(descriptions);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedConceptInput [getIdGenerationStrategy()=");
		builder.append(getIdGenerationStrategy());
		builder.append(", getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getCodeSystemShortName()=");
		builder.append(getCodeSystemShortName());
		builder.append(", getCodeSystemVersionId()=");
		builder.append(getCodeSystemVersionId());
		builder.append(", getTaskId()=");
		builder.append(getTaskId());
		builder.append(", getParentId()=");
		builder.append(getParentId());
		builder.append(", getIsAIdGenerationStrategy()=");
		builder.append(getIsAIdGenerationStrategy());
		builder.append(", getDescriptions()=");
		builder.append(getDescriptions());
		builder.append("]");
		return builder.toString();
	}
}
