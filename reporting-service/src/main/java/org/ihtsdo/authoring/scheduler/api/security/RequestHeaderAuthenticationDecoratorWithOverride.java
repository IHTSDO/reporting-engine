package org.ihtsdo.authoring.scheduler.api.security;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

public class RequestHeaderAuthenticationDecoratorWithOverride extends org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator {
	private String overrideUsername;
	private String overrideRoles;
	private String overrideToken;

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestHeaderAuthenticationDecoratorWithOverride.class);

	public RequestHeaderAuthenticationDecoratorWithOverride(String overrideUsername, String overrideRoles, String overrideToken) {
		this.overrideUsername = overrideUsername;
		this.overrideRoles = overrideRoles;
		this.overrideToken = overrideToken;
	}

	@Override
	protected String getUsername(HttpServletRequest request) {
		if (!Strings.isNullOrEmpty(overrideUsername)) {
			LOGGER.warn("Using authentication override username {}", overrideUsername);
			return overrideUsername;
		} else {
			return super.getUsername(request);
		}
	}

	@Override
	protected String getRoles(HttpServletRequest request) {
		if (!Strings.isNullOrEmpty(overrideRoles)) {
			LOGGER.warn("Using authentication override roles {}", overrideRoles);
			return overrideRoles;
		} else {
			return super.getRoles(request);
		}
	}

	@Override
	protected String getToken(HttpServletRequest request) {
		if (!Strings.isNullOrEmpty(overrideToken)) {
			LOGGER.warn("Using authentication override token supplied from configuration"); // We don't log the token
			return overrideToken;
		} else {
			return super.getToken(request);
		}
	}
}
