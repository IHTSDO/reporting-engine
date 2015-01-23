/*******************************************************************************
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.history;

import java.util.List;


/**
 * Represents a history information element. This element provides detailed information about some 
 * historical modifications made on an element in the past.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link IHistoryInfo#getVersion() <em>Version</em>}</li>
 *   <li>{@link IHistoryInfo#getTimestamp() <em>Timestamp</em>}</li>
 *   <li>{@link IHistoryInfo#getAuthor() <em>Author</em>}</li>
 *   <li>{@link IHistoryInfo#getComments() <em>Comments</em>}</li>
 *   <li>{@link IHistoryInfo#getDetails() <em>History information details</em>}</li>
 * </ul>
 * </p>
 * @see IHistoryVersion
 * @see IHistoryInfoDetails 
 * @author Akos Kitta
 */
public interface IHistoryInfo {

	/**
	 * Returns with the version of the historical modification.
	 * @return the version.
	 * @see IHistoryInfo
 	 */
	IHistoryVersion getVersion();
	
	/**
	 * Returns with the timestamp of the historical modification, expressed 
	 * in milliseconds since January 1, 1970, 00:00:00 GMT.
	 * @return the timestamp.
	 * @see IHistoryInfo
	 */
	long getTimestamp();

	/**
	 * Returns with the name of the author.
	 * @return the name of the author.
	 * @see IHistoryInfo
	 */
	String getAuthor();

	/**
	 * Returns with the comment entered by the author, which may provide additional details about the modification.
	 * @return the author's comment associated with the historical modification.
	 * @see IHistoryInfo
	 */
	String getComments();

	/**
	 * Returns with a {@link List list} of {@link IHistoryInfoDetails detailed information} about the historical modification.
	 * @return a {@link List} of detailed history information.
	 * @see IHistoryInfo
	 */
	List<IHistoryInfoDetails> getDetails();
}
