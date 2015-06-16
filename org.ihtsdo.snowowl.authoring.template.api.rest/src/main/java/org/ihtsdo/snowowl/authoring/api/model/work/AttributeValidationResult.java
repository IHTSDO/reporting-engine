package org.ihtsdo.snowowl.authoring.single.api.model.work;

public class AttributeValidationResult {

	private String typeMessage;
	private String valueMessage;

	public AttributeValidationResult(String typeMessage, String valueMessage) {
		this.typeMessage = typeMessage;
		this.valueMessage = valueMessage;
	}

	public String getTypeMessage() {
		return typeMessage;
	}

	public void setTypeMessage(String typeMessage) {
		this.typeMessage = typeMessage;
	}

	public String getValueMessage() {
		return valueMessage;
	}

	public void setValueMessage(String valueMessage) {
		this.valueMessage = valueMessage;
	}
}
