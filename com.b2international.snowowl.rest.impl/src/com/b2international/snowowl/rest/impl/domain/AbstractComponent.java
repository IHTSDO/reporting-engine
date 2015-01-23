/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.impl.domain;

import com.b2international.snowowl.rest.domain.IComponent;

/**
 * @author apeteri
 */
public abstract class AbstractComponent implements IComponent {

	private String id;
	private boolean released;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public boolean isReleased() {
		return released;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public void setReleased(final boolean released) {
		this.released = released;
	}
}
