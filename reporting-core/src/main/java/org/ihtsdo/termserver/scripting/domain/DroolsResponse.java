package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class DroolsResponse {
	
	public enum Severity {ERROR, WARNING};

	@SerializedName("conceptId")
	@Expose
	private String conceptId;
	
	@SerializedName("componentId")
	@Expose
	private String componentId;
	
	@SerializedName("severity")
	@Expose
	private Severity severity;
	
	@SerializedName("message")
	@Expose
	private String message;
	
	public String getConceptId() {
		return conceptId;
	}
	
	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}
	
	public String getComponentId() {
		return componentId;
	}
	
	public void setComponentId(String componentId) {
		this.componentId = componentId;
	}
	
	public Severity getSeverity() {
		return severity;
	}
	
	public void setSeverity(Severity severity) {
		this.severity = severity;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	@Override
	public String toString() {
		return conceptId + " - " + severity + " - " + message;
	}

}
