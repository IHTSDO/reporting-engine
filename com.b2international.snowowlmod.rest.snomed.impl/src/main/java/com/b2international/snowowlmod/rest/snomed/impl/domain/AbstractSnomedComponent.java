/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import java.util.Date;

import com.b2international.snowowl.rest.impl.domain.AbstractComponent;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponent;

/**
 * @author apeteri
 */
public abstract class AbstractSnomedComponent extends AbstractComponent implements ISnomedComponent {

	private boolean active;
	private Date effectiveTime;
	private String moduleId;

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public Date getEffectiveTime() {
		return effectiveTime;
	}

	@Override
	public String getModuleId() {
		return moduleId;
	}

	public void setActive(final boolean active) {
		this.active = active;
	}

	public void setEffectiveTime(final Date effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public void setModuleId(final String moduleId) {
		this.moduleId = moduleId;
	}
}
