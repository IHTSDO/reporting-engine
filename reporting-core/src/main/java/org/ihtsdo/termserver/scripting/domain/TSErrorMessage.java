package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;

public class TSErrorMessage {
	
	@Expose
	protected Long code;
	
	@Expose
	protected String developerMessage;
	
	public Long getCode() {
		return code;
	}
	public void setCode(Long code) {
		this.code = code;
	}
	public String getDeveloperMessage() {
		return developerMessage;
	}
	public void setDeveloperMessage(String developerMessage) {
		this.developerMessage = developerMessage;
	}
}
