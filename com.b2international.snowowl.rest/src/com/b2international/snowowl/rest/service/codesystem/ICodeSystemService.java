/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service.codesystem;

import java.util.List;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystem;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;

/**
 * Groups methods related to browsing code system metadata.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #getCodeSystems() <em>Retrieve all code systems</em>}
 * <li>{@link #getCodeSystemByShortNameOrOid(String) <em>Retrieve code system by short name or OID</em>}
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface ICodeSystemService {

	/**
	 * Lists all registered code systems.
	 * 
	 * @return a list containing all registered code systems, ordered by short name (never {@code null})
	 */
	List<ICodeSystem> getCodeSystems();

	/**
	 * Retrieves a single code system matches the given shortName or object identifier (OID) parameter, if it exists.
	 * 
	 * @param shortNameOrOid the code system short name or OID to look for, eg. "{@code SNOMEDCT}" or "{@code 3.4.5.6.10000}" (may not be {@code null})
	 * @return the requested code system
	 * @throws CodeSystemNotFoundException if a code system with the given short name or OID is not registered
	 */
	ICodeSystem getCodeSystemByShortNameOrOid(String shortNameOrOid);
}
