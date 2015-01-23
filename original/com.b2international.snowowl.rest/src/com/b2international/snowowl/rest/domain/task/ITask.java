/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.task;

import java.util.Date;

/**
 * Captures information about an editing task.
 * 
 * @author Andras Peteri
 */
public interface ITask {

	/**
	 * Returns the task's description.
	 * @return the task description, eg. "{@code Inactivate relationship on Clinical finding}"
	 */
	String getDescription();
	
	/**
	 * Returns the identifier of this task, typically assigned by an external system.
	 * @return the identifier of this task, eg. "{@code 1245}"
	 */
	String getTaskId();

	/**
	 * Returns the creation or last synchronization time of this task. 
	 * @return the creation or last synchronization time of this task, eg. "{@code 2014-05-09T08:03:55Z}"
	 */
	Date getBaseTimestamp();

	/**
	 * Returns the last update time for this task. 
	 * @return the last update time for this task, eg. "{@code 2014-05-09T11:17:31Z}"
	 */
	Date getLastUpdatedTimestamp();

	/**
	 * Returns the task's current state.
	 * 
	 * @return
	 */
	TaskState getState();
	
}
