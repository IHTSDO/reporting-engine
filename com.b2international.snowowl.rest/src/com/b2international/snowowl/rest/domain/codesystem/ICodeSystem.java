/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.domain.codesystem;

/**
 * Captures metadata about a code system, which holds a set of real-world concepts of medical significance (optionally
 * along with different components forming a description of said concepts) and their corresponding unique code.
 * 
 * @author Akos Kitta
 * @author Andras Peteri
 */
public interface ICodeSystem {

	/**
	 * Returns the assigned object identifier (OID) of this code system.
	 * @return the assigned object identifier of this code system, eg. "{@code 3.4.5.6.10000}" (can be {@code null})
	 */
	String getOid();

	/**
	 * Returns the name of this code system.
	 * @return the name of this code system, eg. "{@code SNOMED Clinical Terms}"
	 */
	String getName();

	/**
	 * Returns the short name of this code system, which is usually an abbreviation of the name.
	 * @return the short name of this code system, eg. "{@code SNOMEDCT}"
	 */
	String getShortName();

	/**
	 * Returns an URL for this code system which points to the maintaining organization's website.
	 * @return the URL of the maintaining organization, eg. "{@code http://example.com/}" (can be {@code null}) 
	 */
	String getOrganizationLink();

	/**
	 * Returns the primary language tag for this code system.
	 * @return the primary language tag, eg. "en_US"
	 */
	String getPrimaryLanguage();

	/**
	 * Returns a short paragraph describing the origins and purpose of this code system.
	 * @return the citation for this code system (can be {@code null})
	 */
	String getCitation();
}
