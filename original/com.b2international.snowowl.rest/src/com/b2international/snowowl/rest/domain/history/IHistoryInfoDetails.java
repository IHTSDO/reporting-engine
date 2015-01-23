/*******************************************************************************
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.history;

/**
 * Provides detailed information about some historical modifications made on an element in the past.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link IHistoryInfoDetails#getComponentType() <em>Component type</em>}</li>
 *   <li>{@link IHistoryInfoDetails#getDescription() <em>Description</em>}</li>
 *   <li>{@link IHistoryInfoDetails#getChangeType() <em>Change type</em>}</li>
 * </ul>
 * </p>
 * @see ChangeType
 * @see IHistoryInfo 
 * @author Akos Kitta
 */
public interface IHistoryInfoDetails {

	/**
	 * Returns with a human readable representation of the modified element's type.
	 * @return the human readable representation of the modified element's type.
	 * @see IHistoryInfoDetails
	 */
	String getComponentType();

	/**
	 * Returns with a detailed description of the modification that has been made on the element.
	 * @return a human readable detailed description of the modification.
	 * @see IHistoryInfoDetails
	 */
	String getDescription();

	/**
	 * Returns with the {@link ChangeType type} of the modification. 
	 * @return the change type.
	 * @see IHistoryInfoDetails
	 */
	ChangeType getChangeType();
	
}