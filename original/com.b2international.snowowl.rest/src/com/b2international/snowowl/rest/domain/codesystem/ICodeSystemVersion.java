/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.codesystem;

import java.util.Date;

/**
 * Encapsulates information about released and pending code system versions of a code system.
 * 
 * @author Akos Kitta
 * @author Andras Peteri
 */
public interface ICodeSystemVersion extends ICodeSystemVersionProperties {

	/**
	 * Returns the date on which this code system version was imported into the server.
	 * @return the import date of this code system version
	 */
	Date getImportDate();

	/**
	 * Returns the date on which this code system version was last modified.
	 * @return the last modification date of this code system version (can be {@code null})
	 */
	Date getLastModificationDate();

	/**
	 * Indicates if any modifications have been made on this code system version after releasing it. 
	 * @return {@code true} if this code system version includes retroactive modifications, {@code false} otherwise
	 */
	boolean isPatched();
}
