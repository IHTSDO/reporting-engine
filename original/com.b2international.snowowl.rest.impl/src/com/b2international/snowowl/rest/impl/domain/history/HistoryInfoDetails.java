/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.impl.domain.history;

import com.b2international.snowowl.rest.domain.history.ChangeType;
import com.b2international.snowowl.rest.domain.history.IHistoryInfoDetails;

/**
 * @author akitta
 *
 */
public class HistoryInfoDetails implements IHistoryInfoDetails {

	private String componentType;
	private String description;
	private ChangeType changeType;
	
	/**
	 * @param componentType
	 * @param description
	 */
	public HistoryInfoDetails(String componentType, String description, ChangeType changeType) {
		this.componentType = componentType;
		this.description = description;
		this.changeType = changeType;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfoDetails#getComponentType()
	 */
	@Override
	public String getComponentType() {
		return componentType;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfoDetails#getDescription()
	 */
	@Override
	public String getDescription() {
		return description;
	}

	/* (non-Javadoc)
	 * @see com.b2international.snowowl.rest.api.domain.IHistoryInfoDetails#getChangeType()
	 */
	@Override
	public ChangeType getChangeType() {
		return changeType;
	}
}
