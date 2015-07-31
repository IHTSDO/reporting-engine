package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonRawValue;

public class Validation {

	public static final String STATUS_SCHEDULED = "SCHEDULED";
	private String message;
	private String status;
	private String validationJson;
	
	public Validation(String errorMsg) {
		message = errorMsg;
	}
	
	public Validation(String status, String msg) {
		this.status = status;
		this.message = msg;
	}

	public String getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@JsonRawValue
	public String getValidationJson() {

		return validationJson;
	}

}
