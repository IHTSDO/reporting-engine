/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.classification;

import java.util.List;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface IRelationshipChangeList {

	List<IRelationshipChange> getChanges();
	
	int getTotal();
}
