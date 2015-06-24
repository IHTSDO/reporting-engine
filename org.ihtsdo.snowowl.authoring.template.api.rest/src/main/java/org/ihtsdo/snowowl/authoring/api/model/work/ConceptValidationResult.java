package org.ihtsdo.snowowl.authoring.single.api.model.work;

import java.util.ArrayList;
import java.util.List;

public class ConceptValidationResult {

	private String termMessage;
	private List<String> parentsMessages;
	private List<List<AttributeValidationResult>> attributesMessages;

	public ConceptValidationResult() {
		parentsMessages = new ArrayList<>();
		attributesMessages = new ArrayList<>();
	}

	public boolean isAnyErrors() {
		if (!termMessage.isEmpty()) {
			return true;
		}
		for (String isARelationshipsMessage : parentsMessages) {
			if (!isARelationshipsMessage.isEmpty()) {
				return true;
			}
		}
		for (List<AttributeValidationResult> attributeGroupMessages : attributesMessages) {
			for (AttributeValidationResult attributeMessages : attributeGroupMessages) {
				if (!attributeMessages.getTypeMessage().isEmpty()) {
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
		parentsMessages.add(message);
	}

	public List<AttributeValidationResult> createAttributeGroup() {
		List<AttributeValidationResult> group = new ArrayList<>();
		attributesMessages.add(group);
		return group;
	}

	public String getTermMessage() {
		return termMessage;
	}

	public void setTermMessage(String termMessage) {
		this.termMessage = termMessage;
	}

	public List<String> getParentsMessages() {
		return parentsMessages;
	}

	public void setParentsMessages(List<String> parentsMessages) {
		this.parentsMessages = parentsMessages;
	}

	public List<List<AttributeValidationResult>> getAttributesMessages() {
		return attributesMessages;
	}

	public void setAttributesMessages(List<List<AttributeValidationResult>> attributesMessages) {
		this.attributesMessages = attributesMessages;
	}
}
