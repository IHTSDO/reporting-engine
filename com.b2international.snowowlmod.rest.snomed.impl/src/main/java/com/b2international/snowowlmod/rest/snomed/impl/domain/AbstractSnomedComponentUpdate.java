/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.ISnomedComponentUpdate;

/**
 * @author apeteri
 */
public abstract class AbstractSnomedComponentUpdate implements ISnomedComponentUpdate {

	private String moduleId;
	private Boolean active;

	@Override
	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(final String moduleId) {
		this.moduleId = moduleId;
	}

	@Override
	public Boolean isActive() {
		return active;
	}

	public void setActive(final Boolean active) {
		this.active = active;
	}
}
