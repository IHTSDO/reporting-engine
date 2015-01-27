/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.impl.domain.AbstractReferenceSet;
import com.b2international.snowowl.rest.snomed.domain.ISnomedReferenceSet;

/**
 * Represents a SNOMED&nbsp;CT reference set.
 * 
 * @author akitta
 * @author apeteri
 */
public class SnomedReferenceSet extends AbstractReferenceSet implements ISnomedReferenceSet {

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SnomedReferenceSet [getId()=");
		builder.append(getId());
		builder.append(", isReleased()=");
		builder.append(isReleased());
		builder.append(", getReferencedComponentCategory()=");
		builder.append(getReferencedComponentCategory());
		builder.append(", getMemberCount()=");
		builder.append(getMemberCount());
		builder.append("]");
		return builder.toString();
	}
}
