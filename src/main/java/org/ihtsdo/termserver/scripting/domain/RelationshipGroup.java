package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;

public class RelationshipGroup {
	List<Relationship> relationships;
	long groupId;
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
	
	
	
}
