package org.ihtsdo.authoring.scheduler.api.rest.config;

import jakarta.servlet.http.HttpServletRequest;
import org.ihtsdo.otf.rest.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class RestControllerAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestControllerAdvice.class);

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    public Map<String, String> handleAuthenticationError(Exception exception, HttpServletRequest request) {
        logError(request, exception);
        return getErrorPayload(exception, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    public Map<String, String> handleAccessDeniedError(Exception exception, HttpServletRequest request) {
        logError(request, exception);
        return getErrorPayload(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BusinessServiceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, String> handleBadRequestError(Exception exception, HttpServletRequest request) {
        logError(request, exception);
        return getErrorPayload(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, String> handleError(Exception exception, HttpServletRequest request) {
        logError(request, exception);
        return getErrorPayload(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, String> getErrorPayload(Exception exception, HttpStatus httpStatus) {
        Map<String, String> errorObject = new HashMap<>();
        errorObject.put("errorMessage", exception.getLocalizedMessage());
        errorObject.put("httpStatus", httpStatus.toString());
        return errorObject;
    }

    private void logError(HttpServletRequest request, Exception exception) {
        LOGGER.error("Request '{}' raised: " + exception.getMessage(), request.getRequestURL(), exception);
    }
}
