/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import java.util.List;

import com.b2international.snowowl.rest.domain.IComponentList;

/**
 * @author apeteri
 */
public abstract class AbstractComponentList<C> implements IComponentList<C> {

	private int totalMembers;
	private List<C> members;

	@Override
	public int getTotalMembers() {
		return totalMembers;
	}

	@Override
	public List<C> getMembers() {
		return members;
	}

	public void setTotalMembers(final int totalMembers) {
		this.totalMembers = totalMembers;
	}

	public void setMembers(final List<C> members) {
		this.members = members;
	}
}
