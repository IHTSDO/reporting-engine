/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import java.util.List;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedConceptInput extends ISnomedComponentInput {

	/**
	 * 
	 * @return
	 */
	List<ISnomedDescriptionInput> getDescriptions();
	
	/**
	 * TODO document
	 * @return
	 */
	String getParentId();
	
	/**
	 * TODO document
	 * @return
	 */
	IdGenerationStrategy getIsAIdGenerationStrategy();
}
