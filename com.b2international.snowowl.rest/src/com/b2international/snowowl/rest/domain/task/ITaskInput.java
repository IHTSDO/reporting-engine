/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.task;

/**
 * Captures information about a task which should be supplied at creation time.
 * 
 * @author Andras Peteri
 */
public interface ITaskInput {

	/**
	 * Returns the task's description.
	 * 
	 * @return the task description, eg. "{@code Inactivate relationship on Clinical finding}"
	 */
	String getDescription();

	/**
	 * Returns the task's unique identifier.
	 * 
	 * @return
	 */
	String getTaskId();
}
