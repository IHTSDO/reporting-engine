/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationshipUpdate;

/**
 * @author apeteri
 */
public class SnomedRelationshipUpdate extends AbstractSnomedComponentUpdate implements ISnomedRelationshipUpdate {

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedRelationshipUpdate [getModuleId()=");
		builder.append(getModuleId());
		builder.append(", isActive()=");
		builder.append(isActive());
		builder.append("]");
		return builder.toString();
	}
}
