/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.impl.domain.AbstractComponentInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponentInput;
import com.b2international.snowowl.rest.snomed.domain.IdGenerationStrategy;

/**
 * @author apeteri
 */
public abstract class AbstractSnomedComponentInput extends AbstractComponentInput implements ISnomedComponentInput {

	private IdGenerationStrategy idGenerationStrategy;
	private String moduleId;

	@Override
	public IdGenerationStrategy getIdGenerationStrategy() {
		return idGenerationStrategy;
	}

	@Override
	public String getModuleId() {
		return moduleId;
	}

	public void setIdGenerationStrategy(final IdGenerationStrategy idGenerationStrategy) {
		this.idGenerationStrategy = idGenerationStrategy;
	}

	public void setModuleId(final String moduleId) {
		this.moduleId = moduleId;
	}
}
