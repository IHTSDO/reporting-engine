package org.ihtsdo.snowowl.authoring.api.model.work;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class WorkingConcept {

	private String id;
	private String term;
	private List<String> isARelationships;
	private List<LinkedHashMap<String, String>> attributeGroups;

	public WorkingConcept() {
		isARelationships = new ArrayList<>();
		attributeGroups = new ArrayList<>();
	}

	public WorkingConcept addIsA(String isARelationship) {
		isARelationships.add(isARelationship);
		return this;
	}

	public LinkedHashMap<String, String> newAttributeGroup() {
		LinkedHashMap<String, String> attributeGroup = new LinkedHashMap<>();
		attributeGroups.add(attributeGroup);
		return attributeGroup;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public List<String> getIsARelationships() {
		return isARelationships;
	}

	public void setIsARelationships(List<String> isARelationships) {
		this.isARelationships = isARelationships;
	}

	public List<LinkedHashMap<String, String>> getAttributeGroups() {
		return attributeGroups;
	}

	public void setAttributeGroups(List<LinkedHashMap<String, String>> attributeGroups) {
		this.attributeGroups = attributeGroups;
	}
}
