/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.classification;

/**
 * TODO
 * @author Andras Peteri
 */
public enum ClassificationStatus {

	/**
	 * 
	 */
	SCHEDULED,

	/**
	 * 
	 */
	RUNNING,

	/**
	 * 
	 */
	COMPLETED,

	/**
	 * 
	 */
	FAILED,

	/**
	 * 
	 */
	CANCELED,
	
	/**
	 * 
	 */
	STALE,

	/**
	 * 
	 */
	SAVING_IN_PROGRESS,
	
	/**
	 * 
	 */
	SAVED, 
	
	/**
	 * 
	 */
	SAVE_FAILED;
}
