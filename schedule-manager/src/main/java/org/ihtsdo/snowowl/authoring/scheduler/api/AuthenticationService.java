package org.ihtsdo.snowowl.authoring.scheduler.api;

import org.ihtsdo.snowowl.authoring.scheduler.api.configuration.WebSecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.route53.model.InvalidArgumentException;

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
		throw new InvalidArgumentException("No 'system' account configured, or authentication token override set");
	}
}
