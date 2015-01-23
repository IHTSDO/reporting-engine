/*******************************************************************************
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.history;


/**
 * Represents a version with a major and a minor version number.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link IHistoryVersion#getMajorVersion() <em>Major version</em>}</li>
 *   <li>{@link IHistoryVersion#getMinorVersion() <em>Minor version</em>}</li>
 * </ul>
 * </p>
 * @see IHistoryInfo
 * @see IHistoryInfoDetails 
 * @author Akos Kitta
 */
public interface IHistoryVersion extends Comparable<IHistoryVersion> {
	
	/**
	 * Returns with the major version number.
	 * @return the major version number.
	 * @see IHistoryVersion
	 */
	int getMajorVersion();
	
	/**
	 * Returns with the minor version number.
	 * @return the minor version number.
	 * @see IHistoryVersion
	 */
	int getMinorVersion();
	
}