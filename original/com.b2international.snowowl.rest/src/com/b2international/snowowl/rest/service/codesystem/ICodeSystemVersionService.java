/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service.codesystem;

import java.util.List;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersion;
import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersionProperties;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemVersionNotFoundException;

/**
 * Groups methods related to browsing released versions of a code system.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #getCodeSystemVersions(String) <em>Retrieve all code system versions by short name</em>}
 * <li>{@link #getCodeSystemVersionById(String, String) <em>Retrieve code system version by short name and ID</em>}
 * <li>{@link #createVersion(String, ICodeSystemVersionProperties) <em>Create new code system version</em>}
 * </ul>
 * 
 * @author Andras Peteri
 * @since 1.0
 */
public interface ICodeSystemVersionService {

	/**
	 * Lists all released code system versions for a single code system with the specified short name, if it exists.
	 * 
	 * @param shortName
	 *            the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @return the requested code system's released versions, ordered by version ID
	 * @throws CodeSystemNotFoundException
	 *             if a code system with the given short name is not registered
	 */
	List<ICodeSystemVersion> getCodeSystemVersions(String shortName);

	/**
	 * Retrieves a single released code system version for the specified code system short name and version identifier, if it exists.
	 * 
	 * @param shortName
	 *            the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param version
	 *            the code system version identifier to look for, eg. "{@code 2014-07-31}" (may not be {@code null})
	 * @return the requested code system version
	 * @throws CodeSystemNotFoundException
	 *             if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException
	 *             if a code system version for the code system with the given identifier is not registered
	 */
	ICodeSystemVersion getCodeSystemVersionById(String shortName, String version);

	/**
	 * Creates a new version in terminology denoted by the given shortName parameter using the given {@link ICodeSystemVersionProperties} as base
	 * properties.
	 * 
	 * @param shortName - the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param properties
	 * @return the newly created code system version
	 */
	ICodeSystemVersion createVersion(String shortName, ICodeSystemVersionProperties properties);
}
