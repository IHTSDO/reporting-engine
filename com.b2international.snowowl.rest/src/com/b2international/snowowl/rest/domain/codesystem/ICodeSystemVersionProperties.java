/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.codesystem;

import java.util.Date;

/**
 * Base properties of a code system version.
 * 
 * @author mczotter
 * @since 1.0
 */
public interface ICodeSystemVersionProperties {

	/**
	 * Returns the description of this code system version.
	 * @return the description of this code system version, eg. "{@code International RF2 Release 2014-07-31}"
	 */
	String getDescription();

	/**
	 * Returns the identifier of this code system version, which is unique within the containing code system. 
	 * @return the code system version identifier, eg. "{@code 2014-07-31}"
	 */
	String getVersion();

	/**
	 * Returns the date on which this code system version will become effective. 
	 * @return the effective date of this code system version (can be {@code null})
	 */
	Date getEffectiveDate();

}
