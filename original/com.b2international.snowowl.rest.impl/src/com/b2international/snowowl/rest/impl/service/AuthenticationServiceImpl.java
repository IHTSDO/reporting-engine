/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service;

import javax.security.auth.login.LoginException;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.datastore.session.IApplicationSessionManager;
import com.b2international.snowowl.rest.service.IAuthenticationService;

/**
 * @author apeteri
 *
 */
public class AuthenticationServiceImpl implements IAuthenticationService {

	@Override
	public boolean authenticate(String username, String password) {

		try {
			ApplicationContext.getServiceForClass(IApplicationSessionManager.class).authenticate(username, password);
			return true;
		} catch (LoginException e) {
			return false;
		}
	}
}
