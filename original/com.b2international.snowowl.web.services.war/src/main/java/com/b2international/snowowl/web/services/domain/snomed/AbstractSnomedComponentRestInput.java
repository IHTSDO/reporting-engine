/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.snomed.domain.IdGenerationStrategy;
import com.b2international.snowowl.rest.snomed.impl.domain.AbstractSnomedComponentInput;
import com.b2international.snowowl.rest.snomed.impl.domain.NamespaceIdGenerationStrategy;
import com.b2international.snowowl.rest.snomed.impl.domain.UserIdGenerationStrategy;

/**
 * @author apeteri
 * @since 1.0
 */
public abstract class AbstractSnomedComponentRestInput<I extends AbstractSnomedComponentInput> {

	private String id;
	private String moduleId;

	/**
	 * @return
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return
	 */
	public String getModuleId() {
		return moduleId;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public void setModuleId(final String moduleId) {
		this.moduleId = moduleId;
	}

	protected abstract I createComponentInput();

	/**
	 * Returns with the component category for the concrete SNOMED&nbsp;CT component input.
	 * @return the component category of the concrete component.
	 */
	protected abstract ComponentCategory getComponentCategory();
	
	protected I toComponentInput(final String version, final String taskId) {
		final I result = createComponentInput();

		result.setCodeSystemShortName("SNOMEDCT");
		result.setCodeSystemVersionId(version);
		result.setTaskId(taskId);

		result.setIdGenerationStrategy(createIdGenerationStrategy(getId()));
		result.setModuleId(getModuleId());

		return result;
	}

	protected IdGenerationStrategy createIdGenerationStrategy(final String idOrNull) {
		// TODO: configurable default namespace?
		if (null == idOrNull) { 
			return new NamespaceIdGenerationStrategy(getComponentCategory(), "1000154");
		} else {
			return new UserIdGenerationStrategy(idOrNull);
		}
	}
}
