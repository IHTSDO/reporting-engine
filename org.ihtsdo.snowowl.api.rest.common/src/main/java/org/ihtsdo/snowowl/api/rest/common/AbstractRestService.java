package org.ihtsdo.snowowl.api.rest.common;

import com.b2international.snowowl.core.exceptions.ApiError;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ConflictException;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.catalina.connector.ClientAbortException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
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
	private static final String BAD_REQUEST_MESSAGE = "Bad Request";
	private static final String ILLEGAL_STATE_MESSAGE = "Bad request - illegal state.";
	private static final String NOT_IMPLEMENTED_MESSAGE = "Not implemented";

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
		return getRestApiError(getApiError(ex, GENERIC_USER_MESSAGE), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler
	@ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
	public @ResponseBody RestApiError handle(final ClientAbortException ex) {
		LOG.info("Client Abort Exception during processing of a request. Username '{}'", ControllerHelper.getUsername(), ex);
		return getRestApiError(getApiError(ex, GENERIC_USER_MESSAGE), HttpStatus.REQUEST_TIMEOUT);
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
		return getRestApiError(getApiError(ex, BAD_REQUEST_MESSAGE), HttpStatus.BAD_REQUEST);
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

	@ExceptionHandler
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public @ResponseBody RestApiError handle(final ResourceNotFoundException ex) {
		return getRestApiError(getApiError(ex), HttpStatus.NOT_FOUND);
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
		LOG.info("UnsupportedOperationException", ex);
		return getRestApiError(getApiError(ex, NOT_IMPLEMENTED_MESSAGE), HttpStatus.NOT_IMPLEMENTED);
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
		return getRestApiError(getApiError(ex, ILLEGAL_STATE_MESSAGE), HttpStatus.BAD_REQUEST);
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
		if (ex.getCause() != null) {
			LOG.info("Conflict with cause", ex);
		}
		return getRestApiError(ex.toApiError(), HttpStatus.CONFLICT);
	}

	/**
	 * @param ex
	 * @return {@link org.ihtsdo.snowowl.api.rest.common.RestApiError} instance with detailed messages
	 */
	@ExceptionHandler
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public @ResponseBody
	RestApiError handle(final AccessDeniedException ex) {
		LOG.warn("Exception during processing of a request", ex);
		return getRestApiError(getApiError(ex, ACCESS_DENIED_MESSAGE), HttpStatus.UNAUTHORIZED);
	}

	private RestApiError getRestApiError(ApiError apiError, HttpStatus httpStatus) {
		return RestApiError.of(apiError).build(httpStatus.value());
	}
	
	private ApiError getApiError(Exception ex) {
		return getApiError(ex, ex.getMessage());
	}
	
	private ApiError getApiError(Exception ex, String message) {
		return ApiError.Builder.of(message).developerMessage(getDeveloperMessage(ex)).build();
	}

	private String getDeveloperMessage(Exception ex) {
		if (ex instanceof UnsupportedOperationException) {
			return ex.getMessage() == null ? "Unsupported Operation" : ex.getMessage();
		}
		return ex.getMessage();
	}
}
