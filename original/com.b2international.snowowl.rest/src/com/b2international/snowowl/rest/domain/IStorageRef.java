/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain;

/**
 * Points to a versioned component storage space of a code system, on a particular task. 
 *
 * @author Andras Peteri
 */
public interface IStorageRef {

	/**
	 * Returns the code system short name, eg. "{@code SNOMEDCT}"
	 * @return the code system short name
	 */
	String getShortName();

	/**
	 * Returns the code system version identifier, eg. "{@code 2014-01-31}".
	 * @return the code system version identifier
	 */
	String getVersion();

	/**
	 * Returns the task identifier, eg. "{@code 1747}". A {@code null} value points to the repository version,
	 * when the component is not part of an editing task.
	 * @return the task identifier, or {@code null} in case of a component on a version
	 */
	String getTaskId();
}
