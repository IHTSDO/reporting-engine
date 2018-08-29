package org.ihtsdo.snowowl.authoring.scheduler.api.security;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RequestHeaderAuthenticationDecorator extends org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator {

	private static final String USERNAME = "X-AUTH-username";
	private static final String ROLES = "X-AUTH-roles";
	private static final String TOKEN = "X-AUTH-token";

	private String overrideUsername;
	private String overrideRoles;
	private String overrideToken;

	public RequestHeaderAuthenticationDecorator(String overrideUsername, String overrideRoles, String overrideToken) {
		this.overrideUsername = overrideUsername;
		this.overrideRoles = overrideRoles;
		this.overrideToken = overrideToken;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String username = request.getHeader(USERNAME);
		String roles = request.getHeader(ROLES);
		String token = request.getHeader(TOKEN);
		Logger logger = LoggerFactory.getLogger(getClass());
		if (!Strings.isNullOrEmpty(overrideUsername)) {
			username = overrideUsername;
			logger.warn("Using authentication override username {}", username);
		}
		if (!Strings.isNullOrEmpty(overrideRoles)) {
			roles = overrideRoles;
			logger.warn("Using authentication override roles {}", roles);
		}
		if (!Strings.isNullOrEmpty(overrideToken)) {
			token = overrideToken;
			logger.warn("Using authentication override token"); // We don't log the token
		}
		List decoratedRoles = roles != null ? AuthorityUtils.commaSeparatedStringToAuthorityList(roles) : new ArrayList();
		decoratedRoles.addAll(SecurityContextHolder.getContext().getAuthentication().getAuthorities());
		PreAuthenticatedAuthenticationToken decoratedAuthentication = new PreAuthenticatedAuthenticationToken(username, token, decoratedRoles);
		decoratedAuthentication.setDetails(SecurityContextHolder.getContext().getAuthentication().getDetails());
		SecurityContextHolder.getContext().setAuthentication(decoratedAuthentication);
		filterChain.doFilter(request, response);
	}

	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null || !authentication.isAuthenticated();
	}
}
