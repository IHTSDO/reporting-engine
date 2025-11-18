package org.ihtsdo.authoring.scheduler.api;

import org.ihtsdo.authoring.scheduler.api.configuration.WebSecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationService {

	@Autowired
    WebSecurityConfig config;

	public String getSystemAuthorisation() {
		String overrideToken = config.getOverrideToken();
		if ( overrideToken != null) {
			return overrideToken;
		}
		
		//TODO Need a solution for obtaining a system token
		throw new RuntimeException("No 'system' account configured, or authentication token override set");
	}
}
