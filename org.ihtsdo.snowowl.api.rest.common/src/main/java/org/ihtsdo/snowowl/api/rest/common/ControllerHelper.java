package org.ihtsdo.snowowl.api.rest.common;

import org.springframework.security.core.context.SecurityContextHolder;

public class ControllerHelper {

	public static String getUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
	}
}
