package org.ihtsdo.snowowl.authoring.single.api.model.work;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class WorkingConcept {

	private String id;
	private String term;
	private List<String> parents;
	private List<LinkedHashMap<String, String>> attributeGroups;

	public WorkingConcept() {
		parents = new ArrayList<>();
		attributeGroups = new ArrayList<>();
	}

	public WorkingConcept addIsA(String isARelationship) {
		parents.add(isARelationship);
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

	public List<String> getParents() {
		return parents;
	}

	public void setParents(List<String> parents) {
		this.parents = parents;
	}

	@JsonProperty(value = "attributes")
	public List<LinkedHashMap<String, String>> getAttributeGroups() {
		return attributeGroups;
	}

	public void setAttributeGroups(List<LinkedHashMap<String, String>> attributeGroups) {
		this.attributeGroups = attributeGroups;
	}
}
