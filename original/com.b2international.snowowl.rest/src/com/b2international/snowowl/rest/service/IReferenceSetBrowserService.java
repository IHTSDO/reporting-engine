/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

import com.b2international.snowowl.rest.domain.IReferenceSet;
import com.b2international.snowowl.rest.domain.IReferenceSetMember;
import com.b2international.snowowl.rest.domain.IReferenceSetMemberList;
import com.b2international.snowowl.rest.domain.IComponentRef;

/**
 * Terminology independent interface of the Reference Set Browser Service.
 * 
 * @author Andras Peteri
 */
public interface IReferenceSetBrowserService<R extends IReferenceSet, M extends IReferenceSetMember> {
	
	/**
	 * Returns reference set members identified by the unique reference set identifier.
	 * @param referenceSetRef a component reference pointing to a reference set (may not be {@code null})
	 * @param offset
	 * @param limit
	 * @return the members of the reference set, sorted by identifier
	 */
	IReferenceSetMemberList<M> getMembers(final IComponentRef referenceSetRef, final int offset, final int limit);
	
	/**
	 * Returns reference set members referring to the specified component.
	 * @param componentRef the reference pointing to the component (may not be {@code null})
	 * @param offset
	 * @param limit
	 * @return reference set members referring to the specified component, sorted by identifier
	 */
	IReferenceSetMemberList<M> getReferringMembers(final IComponentRef componentRef, final int offset, final int limit);
}
