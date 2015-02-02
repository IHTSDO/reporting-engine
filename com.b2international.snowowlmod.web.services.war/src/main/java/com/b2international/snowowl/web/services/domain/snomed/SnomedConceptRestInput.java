/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import static com.b2international.snowowl.rest.domain.ComponentCategory.CONCEPT;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Collections;
import java.util.List;

import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedConceptInput;
import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedDescriptionInput;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedConceptRestInput extends AbstractSnomedComponentRestInput<SnomedConceptInput> {

	private List<SnomedDescriptionRestInput> descriptions = Collections.emptyList();
	private String isAId;
	private String parentId;

	/**
	 * @return
	 */
	public List<SnomedDescriptionRestInput> getDescriptions() {
		return descriptions;
	}

	/**
	 * @return
	 */
	public String getIsAId() {
		return isAId;
	}

	/**
	 * @return
	 */
	public String getParentId() {
		return parentId;
	}

	public void setDescriptions(List<SnomedDescriptionRestInput> descriptions) {
		this.descriptions = descriptions;
	}

	public void setIsAId(final String isAId) {
		this.isAId = isAId;
	}

	public void setParentId(final String parentId) {
		this.parentId = parentId;
	}

	@Override
	protected SnomedConceptInput createComponentInput() {
		return new SnomedConceptInput();
	}

	/**
	 * @return
	 */
	@Override
	public SnomedConceptInput toComponentInput(final String version, final String taskId) {
		final SnomedConceptInput result = super.toComponentInput(version, taskId);

		result.setIsAIdGenerationStrategy(createIdGenerationStrategy(getIsAId()));

		final List<SnomedDescriptionInput> descriptionInputs = newArrayList();
		for (SnomedDescriptionRestInput restDescription : getDescriptions()) {
			descriptionInputs.add(restDescription.toComponentInput(version, taskId));
		}
		result.setDescriptions(descriptionInputs);

		result.setParentId(getParentId());

		return result;
	}

	@Override
	protected ComponentCategory getComponentCategory() {
		return CONCEPT;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedConceptRestInput [getId()=");
		builder.append(getId());
		builder.append(", getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getDescriptions()=");
		builder.append(getDescriptions());
		builder.append(", getIsAId()=");
		builder.append(getIsAId());
		builder.append(", getParentId()=");
		builder.append(getParentId());
		builder.append("]");
		return builder.toString();
	}
}
