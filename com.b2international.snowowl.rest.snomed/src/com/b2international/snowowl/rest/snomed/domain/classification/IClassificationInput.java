/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.classification;

/**
 * TODO
 * @author Andras Peteri
 */
public interface IClassificationInput {

	/**
	 * 
	 * @return the identifier of the reasoner to use, or {@code null} if the server default should be used instead
	 */
	String getReasonerId();

	/**
	 * 
	 * @return
	 */
	String getUserId();
}
