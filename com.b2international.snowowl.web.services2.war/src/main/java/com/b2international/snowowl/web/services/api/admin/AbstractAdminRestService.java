/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.b2international.snowowl.rest.exception.NotFoundException;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;

/**
 * @author apeteri
 */
public abstract class AbstractAdminRestService {

	/** Displayed when the caught exception contains no message. */
	private static final String DEFAULT_MESSAGE = "An error occurred while processing the request.";

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public @ResponseBody String handleNotFoundException(final NotFoundException e) {
		return handleException(e);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody String handleIllegalArgumentException(final IllegalArgumentException e) {
		return handleException(e);
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public @ResponseBody String handleException(final Exception e) {
		return toExceptionMessage(e);
	}

	private String toExceptionMessage(final Exception e) {
		return Optional.fromNullable(e.getMessage()).or(DEFAULT_MESSAGE);
	}

	protected String joinStrings(final List<String> stringList) {
		return Joiner.on("\n").join(stringList);
	}
}
