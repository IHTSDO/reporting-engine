/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.task;

/**
 * @author mczotter
 */
public interface ITaskChangeRequest {

	/**
	 * Returns the desired state of the task.
	 * 
	 * @return
	 */
	TaskState getState();

}
