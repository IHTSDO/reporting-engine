/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api;

import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * {@link RestApiError} represents a generic multi purpose user AND developer friendly error/exception representation, which should be used in all
 * cases when mapping various exceptions to responses.
 * 
 * @author mczotter
 * @since 3.7
 */
@ApiModel("Error Response")
public class RestApiError {

	@ApiModelProperty(required = true)
	private int status;
	
	@ApiModelProperty(required = false)
	private Integer code;
	
	@ApiModelProperty(required = true)
	private String message = "Request failed";
	
	@ApiModelProperty(required = true)
	private String developerMessage;

	private RestApiError() {
		// intentionally ignored, use the builder
	}

	/**
	 * Returns an HTTP status code associated with this error.
	 * 
	 * @return
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Custom application specific error code associated with the response.
	 * 
	 * @return
	 */
	public Integer getCode() {
		return code;
	}

	/**
	 * Returns a user-friendly error message, meaning it can be used in User interfaces to show the error to the end-user. It should never contain any
	 * kind of technical information, that should go to the {@link #getDeveloperMessage()}.
	 * 
	 * @return
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Returns a developer-friendly (more verbose than the {@link #getMessage()}) error message. It can be used to investigate problems and the cause
	 * of them. Usually can contain technical information, parameter values, etc.
	 * 
	 * @return
	 */
	public String getDeveloperMessage() {
		return developerMessage;
	}

	private void setStatus(int status) {
		this.status = status;
	}
	
	public void setCode(int code) {
		this.code = code;
	}

	private void setMessage(String message) {
		this.message = message;
	}

	private void setDeveloperMessage(String developerMessage) {
		this.developerMessage = developerMessage;
	}
	
	/**
	 * Return a new {@link Builder} to build a new {@link RestApiError} representation.
	 * 
	 * @param status
	 * @return
	 */
	public static Builder of(int status) {
		return new Builder(status);
	}

	/**
	 * Builder responsible for building {@link RestApiError} in a fluent way.
	 * 
	 * @author mczotter
	 * @since 3.7
	 */
	public static final class Builder {

		private RestApiError error;

		private Builder(int status) {
			this.error = new RestApiError();
			this.error.setStatus(status);
		}

		public Builder code(int code) {
			this.error.setCode(code);
			return this;
		}
		
		public Builder message(String message) {
			this.error.setMessage(message);
			return this;
		}

		public Builder developerMessage(String message) {
			this.error.setDeveloperMessage(message);
			return this;
		}

		public RestApiError build() {
			return this.error;
		}

	}

}
