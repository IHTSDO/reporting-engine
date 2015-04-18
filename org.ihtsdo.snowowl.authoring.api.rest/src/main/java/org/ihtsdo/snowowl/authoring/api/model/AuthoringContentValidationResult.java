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

	public boolean isAnyErrors() {
		for (String isARelationshipsMessage : isARelationshipsMessages) {
			if (!isARelationshipsMessage.isEmpty()) {
				return true;
			}
		}
		for (String s : attributesMessages.keySet()) {
			if (!s.isEmpty()) {
				return true;
			}
		}
		for (String s : attributesMessages.values()) {
			if (!s.isEmpty()) {
				return true;
			}
		}
		return false;
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
