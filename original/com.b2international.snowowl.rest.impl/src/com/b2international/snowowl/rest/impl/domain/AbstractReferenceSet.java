/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.domain.IReferenceSet;

/**
 * @author apeteri
 */
public abstract class AbstractReferenceSet extends AbstractComponent implements IReferenceSet {

	private ComponentCategory referencedComponentCategory;
	private int memberCount;

	@Override
	public ComponentCategory getReferencedComponentCategory() {
		return referencedComponentCategory;
	}

	@Override
	public int getMemberCount() {
		return memberCount;
	}

	public void setReferencedComponentCategory(final ComponentCategory referencedComponentCategory) {
		this.referencedComponentCategory = referencedComponentCategory;
	}

	public void setMemberCount(final int memberCount) {
		this.memberCount = memberCount;
	}
}
