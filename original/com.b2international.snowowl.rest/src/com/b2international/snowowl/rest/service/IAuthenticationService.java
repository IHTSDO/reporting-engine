/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

/**
 * TODO
 * @author Andras Peteri
 */
public interface IAuthenticationService {

	/**
	 * @param username
	 * @param password
	 * @return
	 */
	boolean authenticate(String username, String password);
}
