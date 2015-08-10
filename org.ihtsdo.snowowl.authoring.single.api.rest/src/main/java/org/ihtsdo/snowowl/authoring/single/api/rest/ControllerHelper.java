package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class ControllerHelper {

	public static UserDetails getUserDetails() {
		return (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}

	public static String getUsername() {
		return getUserDetails().getUsername();
	}
}
