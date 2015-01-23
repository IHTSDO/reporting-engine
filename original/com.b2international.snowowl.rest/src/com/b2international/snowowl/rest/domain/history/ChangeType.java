/*******************************************************************************
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.history;


/**
 * Enumeration type for indicating historical changes on an element.
 <p>
 * The following types are available:
 * <ul>
 *   <li>{@link ChangeType#NEW <em>New</em>}</li>
 *   <li>{@link ChangeType#DETACHED <em>Detached</em>}</li>
 *   <li>{@link ChangeType#CHANGED <em>Changed</em>}</li>
 * </ul>
 * </p>
 * @author Akos Kitta
 * @see IHistoryInfo
 * @see IHistoryInfoDetails
 *  
 */
public enum ChangeType {
	
	/**Indicates the creation of an element.
	 * @see ChangeType
	 * @see ChangeType#DETACHED
	 * @see ChangeType#CHANGED 
	 * */
	NEW,
	/**Indicates the deletion of an element.
	 * @see ChangeType
	 * @see ChangeType#CHANGED 
	 * @see ChangeType#NEW 
	 * */
	DETACHED,
	/**Indicates that some modification has been made on the element.
	 * @see ChangeType
	 * @see ChangeType#DETACHED
	 * @see ChangeType#NEW 
	 * */
	CHANGED
}