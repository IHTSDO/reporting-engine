package org.ihtsdo.snowowl.authoring.api.model;

import java.util.ArrayList;
import java.util.List;

public class AuthoringContentValidationResult {

	private List<String> isARelationshipsMessages;
	private List<List<AttributeValidationResult>> attributeGroupsMessages;

	public AuthoringContentValidationResult() {
		isARelationshipsMessages = new ArrayList<>();
		attributeGroupsMessages = new ArrayList<>();
	}

	public boolean isAnyErrors() {
		for (String isARelationshipsMessage : isARelationshipsMessages) {
			if (!isARelationshipsMessage.isEmpty()) {
				return true;
			}
		}
		for (List<AttributeValidationResult> attributeGroupMessages : attributeGroupsMessages) {
			for (AttributeValidationResult attributeMessages : attributeGroupMessages) {
				if (!attributeMessages.getDomainMessage().isEmpty()) {
					return true;
				}
				if (!attributeMessages.getValueMessage().isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	public void addIsARelationshipsMessage(String message) {
		isARelationshipsMessages.add(message);
	}

	public List<AttributeValidationResult> createAttributeGroup() {
		List<AttributeValidationResult> group = new ArrayList<>();
		attributeGroupsMessages.add(group);
		return group;
	}

	public List<String> getIsARelationshipsMessages() {
		return isARelationshipsMessages;
	}

	public void setIsARelationshipsMessages(List<String> isARelationshipsMessages) {
		this.isARelationshipsMessages = isARelationshipsMessages;
	}

	public List<List<AttributeValidationResult>> getAttributeGroupsMessages() {
		return attributeGroupsMessages;
	}

	public void setAttributeGroupsMessages(List<List<AttributeValidationResult>> attributeGroupsMessages) {
		this.attributeGroupsMessages = attributeGroupsMessages;
	}
}
