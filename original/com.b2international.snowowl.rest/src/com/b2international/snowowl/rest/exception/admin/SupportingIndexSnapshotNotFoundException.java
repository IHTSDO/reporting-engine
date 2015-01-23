/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.exception.admin;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Thrown when an index snapshot for a supporting index can not be found for a given snapshot identifier.
 * 
 * @author Andras Peteri
 */
public class SupportingIndexSnapshotNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new instance with the specified supporting index snapshot identifier.
	 * 
	 * @param indexId the identifier of the snapshot which could not be found (may not be {@code null})
	 */
	public SupportingIndexSnapshotNotFoundException(final String snapshotId) {
		super("Supporting index snapshot", snapshotId);
	}
}
