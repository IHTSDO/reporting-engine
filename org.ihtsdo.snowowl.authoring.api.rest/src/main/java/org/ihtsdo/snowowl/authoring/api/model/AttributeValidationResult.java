package org.ihtsdo.snowowl.authoring.api.model;

public class AttributeValidationResult {

	private String domainMessage;
	private String valueMessage;

	public AttributeValidationResult(String domainMessage, String valueMessage) {
		this.domainMessage = domainMessage;
		this.valueMessage = valueMessage;
	}

	public String getDomainMessage() {
		return domainMessage;
	}

	public void setDomainMessage(String domainMessage) {
		this.domainMessage = domainMessage;
	}

	public String getValueMessage() {
		return valueMessage;
	}

	public void setValueMessage(String valueMessage) {
		this.valueMessage = valueMessage;
	}
}
