package org.ihtsdo.snowowl.authoring.api.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class AuthoringContent {

	private List<String> isARelationships;
	private LinkedHashMap<String, String> attributes;

	public AuthoringContent() {
		isARelationships = new ArrayList<>();
		attributes = new LinkedHashMap<>();
	}

	public AuthoringContent addIsA(String isARelationship) {
		isARelationships.add(isARelationship);
		return this;
	}

	public List<String> getIsARelationships() {
		return isARelationships;
	}

	public void setIsARelationships(List<String> isARelationships) {
		this.isARelationships = isARelationships;
	}

	public LinkedHashMap<String, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(LinkedHashMap<String, String> attributes) {
		this.attributes = attributes;
	}
}
