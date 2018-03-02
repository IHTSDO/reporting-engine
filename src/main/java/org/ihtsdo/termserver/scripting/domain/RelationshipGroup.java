package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RelationshipGroup {
	List<Relationship> relationships;
	long groupId;
	
	//Generic flag to say if group should be highlighted for some reason, eg cause a template match to fail
	String indicators = "";
	//Generic holder to record some property of this relationship which we need to deal with
	List<Concept> issues;
	
	RelationshipGroup (int groupId, List<Relationship> relationships) {
		this.groupId = groupId;
		this.relationships = relationships;
	}
	
	public RelationshipGroup(long groupId, Relationship r) {
		relationships = new ArrayList<>();
		this.groupId = groupId;
		relationships.add(r);
	}

	public List<Relationship> getRelationships() {
		return relationships;
	}
	
	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}
	
	public long getGroupId() {
		return groupId;
	}
	
	public List<Concept> getIssue() {
		return issues;
	}

	public void setIssues(List<Concept> issue) {
		this.issues = issue;
	}
	
	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}
	
	public void addIssue (Concept c) {
		if (issues == null) {
			issues = new ArrayList<>();
		}
		issues.add(c);
	}
	
	@Override
	public String toString() {
		return indicators + "{ " + relationships.stream()
				.sorted((r1, r2) -> r1.getType().getFsn().compareTo(r2.getType().getFsn()))
				.map(i -> i.toString())
				.collect (Collectors.joining(", ")) + " }";
	}
	
	public void addIndicator(char indicator) {
		this.indicators += indicator;
	}
	
}
