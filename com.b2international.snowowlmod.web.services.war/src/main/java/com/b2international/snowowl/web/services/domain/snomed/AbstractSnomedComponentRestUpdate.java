/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import com.b2international.snowowl.rest.snomed.impl.domain.AbstractSnomedComponentUpdate;

/**
 * @author apeteri
 * @since 1.0
 */
public abstract class AbstractSnomedComponentRestUpdate<U extends AbstractSnomedComponentUpdate> {

	private String moduleId;
	private Boolean active;

	public Boolean isActive() {
		return active;
	}

	public void setActive(final Boolean active) {
		this.active = active;
	}


	/**
	 * @return
	 */
	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(final String moduleId) {
		this.moduleId = moduleId;
	}

	protected abstract U createComponentUpdate();

	public U toComponentUpdate() {
		final U result = createComponentUpdate();
		result.setModuleId(getModuleId());
		result.setActive(isActive());
		return result;
	}
}
