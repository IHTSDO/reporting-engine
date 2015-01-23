/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

import java.util.List;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface IReferenceSetMemberList<M extends IReferenceSetMember> {

	/**
	 * TODO document
	 * @return
	 */
	int getTotalMembers();
	
	/**
	 * TODO document
	 * @return
	 */
	List<M> getMembers();
}
