/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import com.b2international.snowowl.rest.domain.IComponentRef;
import com.google.common.collect.Ordering;

/**
 * @author apeteri
 */
public class ComponentRef extends StorageRef implements InternalComponentRef {

	private String componentId;

	@Override
	public String getComponentId() {
		return componentId;
	}

	public void setComponentId(final String componentId) {
		this.componentId = componentId;
	}

	@Override
	public final int compareTo(final IComponentRef other) {
		int result = 0;
		if (result == 0) { result = getShortName().compareTo(other.getShortName()); }
		if (result == 0) { result = getVersion().compareTo(other.getVersion()); }
		if (result == 0) { result = Ordering.natural().nullsFirst().compare(getTaskId(), other.getTaskId()); }
		if (result == 0) { result = getComponentId().compareTo(other.getComponentId()); }
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ComponentRef [getShortName()=");
		builder.append(getShortName());
		builder.append(", getVersion()=");
		builder.append(getVersion());
		builder.append(", getTaskId()=");
		builder.append(getTaskId());
		builder.append(", componentId=");
		builder.append(componentId);
		builder.append("]");
		return builder.toString();
	}
}
