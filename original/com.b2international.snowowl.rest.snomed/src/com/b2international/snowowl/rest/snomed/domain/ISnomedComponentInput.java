/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.rest.domain.IComponentInput;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedComponentInput extends IComponentInput {

	/**
	 * TODO document 
	 * @return
	 */
	IdGenerationStrategy getIdGenerationStrategy();

	/**
	 * TODO document
	 * @return
	 */
	String getModuleId();
}