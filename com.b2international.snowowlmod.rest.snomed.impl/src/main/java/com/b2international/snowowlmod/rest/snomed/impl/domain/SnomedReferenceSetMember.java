/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import java.util.Date;

import com.b2international.snowowl.rest.impl.domain.AbstractReferenceSetMember;
import com.b2international.snowowl.rest.snomed.domain.ISnomedReferenceSetMember;

/**
 * @author akitta
 * @author apeteri
 */
public class SnomedReferenceSetMember extends AbstractReferenceSetMember implements ISnomedReferenceSetMember {

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

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedReferenceSetMember [getId()=");
		builder.append(getId());
		builder.append(", isReleased()=");
		builder.append(isReleased());
		builder.append(", getReferenceSetId()=");
		builder.append(getReferenceSetId());
		builder.append(", getReferencedComponentId()=");
		builder.append(getReferencedComponentId());
		builder.append(", isActive()=");
		builder.append(isActive());
		builder.append(", getEffectiveTime()=");
		builder.append(getEffectiveTime());
		builder.append(", getModuleId()=");
		builder.append(getModuleId());
		builder.append("]");
		return builder.toString();
	}
}
