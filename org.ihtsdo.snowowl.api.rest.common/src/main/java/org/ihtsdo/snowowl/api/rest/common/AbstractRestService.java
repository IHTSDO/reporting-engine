package org.ihtsdo.snowowl.api.rest.common;

import com.b2international.snowowl.core.exceptions.ApiError;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ConflictException;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class AbstractRestService {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractRestService.class);
	private static final String GENERIC_USER_MESSAGE = "Something went wrong during the processing of your request.";
	private static final String ACCESS_DENIED_MESSAGE = "You do not have required privileges to perform this operation.";

	/**
	 * The currently supported versioned media type of the snowowl RESTful API.
	 */
	public static final String V1_MEDIA_TYPE = "application/vnd.com.b2international.snowowl-v1+json";

	/**
	 * Generic <b>Internal Server Error</b> exception handler, serving as a fallback for RESTful client calls.
	 * 
	 * @param ex
	 * @return {@link org.ihtsdo.snowowl.api.rest.common.RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public @ResponseBody
	RestApiError handle(final Exception ex) {
		LOG.error("Exception during processing of a request. Username '{}'", ControllerHelper.getUsername(), ex);
		return RestApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value()).message(GENERIC_USER_MESSAGE).developerMessage(getDeveloperMessage(ex)).build();
	}
	
	/**
	 * Exception handler converting any {@link JsonMappingException} to an <em>HTTP 400</em>.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody RestApiError handle(HttpMessageNotReadableException ex) {
		LOG.warn("Exception during processing of a JSON document", ex);
		return RestApiError.of(HttpStatus.BAD_REQUEST.value()).message("Bad Request").developerMessage(getDeveloperMessage(ex)).build();
	}

	/**
	 * <b>Not Found</b> exception handler. All {@link NotFoundException not found exception}s are mapped to {@link HttpStatus#NOT_FOUND
	 * <em>404 Not Found</em>} in case of the absence of an instance resource.
	 *
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public @ResponseBody RestApiError handle(final NotFoundException ex) {
		return getRestApiError(ex.toApiError(), HttpStatus.NOT_FOUND);
	}

	private RestApiError getRestApiError(ApiError apiError, HttpStatus httpStatus) {
		return RestApiError.of(httpStatus.value()).message(apiError.getMessage()).developerMessage(apiError.getDeveloperMessage()).build();
	}

	/**
	 * Exception handler to return <b>Not Implemented</b> when an {@link UnsupportedOperationException} is thrown from the underlying system.
	 * 
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
	public @ResponseBody RestApiError handle(UnsupportedOperationException ex) {
		return RestApiError.of(HttpStatus.NOT_IMPLEMENTED.value()).developerMessage(getDeveloperMessage(ex))
				.build();
	}

	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 *
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody RestApiError handle(final BadRequestException ex) {
		return getRestApiError(ex.toApiError(), HttpStatus.BAD_REQUEST);
	}

	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link IllegalStateException} is thrown from the underlying system.
	 *
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public @ResponseBody RestApiError handle(final IllegalStateException ex) {
		return RestApiError.of(HttpStatus.BAD_REQUEST.value()).message("Bad request - illegal state.").developerMessage(getDeveloperMessage(ex)).build();
	}

	/**
	 * Exception handler to return <b>Bad Request</b> when an {@link BadRequestException} is thrown from the underlying system.
	 *
	 * @param ex
	 * @return {@link RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.CONFLICT)
	public @ResponseBody RestApiError handle(final ConflictException ex) {
		return getRestApiError(ex.toApiError(), HttpStatus.CONFLICT);
	}

	private String getDeveloperMessage(Exception ex) {
		if (ex instanceof UnsupportedOperationException) {
			return ex.getMessage() == null ? "Unsupported Operation" : ex.getMessage();
		}
		return ex.getMessage();
	}

	/**
	 *  org.springframework.security.access.AccessDeniedException 
	 * @param ex
	 * @return {@link org.ihtsdo.snowowl.api.rest.common.RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public @ResponseBody
	RestApiError handle(final AccessDeniedException ex) {
		LOG.warn("Exception during processing of a request", ex);
		return RestApiError.of(HttpStatus.UNAUTHORIZED.value()).message(ACCESS_DENIED_MESSAGE).developerMessage(getDeveloperMessage(ex)).build();
	}

}