package org.ihtsdo.snowowl.authoring.api.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class AuthoringContentValidationResult {

	private List<String> isARelationshipsMessages;
	private LinkedHashMap<String, String> attributesMessages;

	public AuthoringContentValidationResult() {
		isARelationshipsMessages = new ArrayList<>();
		attributesMessages = new LinkedHashMap<>();
	}

	public void addIsARelationshipsMessage(String message) {
		isARelationshipsMessages.add(message);
	}

	public List<String> getIsARelationshipsMessages() {
		return isARelationshipsMessages;
	}

	public void setIsARelationshipsMessages(List<String> isARelationshipsMessages) {
		this.isARelationshipsMessages = isARelationshipsMessages;
	}

	public LinkedHashMap<String, String> getAttributesMessages() {
		return attributesMessages;
	}

	public void setAttributesMessages(LinkedHashMap<String, String> attributesMessages) {
		this.attributesMessages = attributesMessages;
	}
}
