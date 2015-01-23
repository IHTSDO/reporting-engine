/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import com.b2international.snowowl.rest.domain.IReferenceSetMember;

/**
 * @author apeteri
 */
public abstract class AbstractReferenceSetMember extends AbstractComponent implements IReferenceSetMember {

	private String referenceSetId;
	private String referencedComponentId;

	@Override
	public String getReferenceSetId() {
		return referenceSetId;
	}

	@Override
	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public void setReferenceSetId(final String referenceSetId) {
		this.referenceSetId = referenceSetId;
	}

	public void setReferencedComponentId(final String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}
}
