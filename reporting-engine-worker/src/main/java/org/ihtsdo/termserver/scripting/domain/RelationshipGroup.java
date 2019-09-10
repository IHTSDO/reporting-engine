package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RelationshipGroup {
	List<Relationship> relationships = new ArrayList<>();
	int groupId;
	
	//Generic flag to say if group should be highlighted for some reason, eg cause a template match to fail
	String indicators = "";
	//Generic holder to record some property of this relationship which we need to deal with
	List<Concept> issues;
	
	public RelationshipGroup clone() {
		RelationshipGroup clone = new RelationshipGroup(groupId);
		for (Relationship r : relationships) {
			clone.addRelationship(r.clone());
		}
		return clone;
	}
	
	public RelationshipGroup (int groupId) {
		this.groupId = groupId;
		this.relationships = new ArrayList<>();
	}
	
	public RelationshipGroup (int groupId, List<Relationship> relationships) {
		this.groupId = groupId;
		this.relationships = relationships;
	}
	
	public RelationshipGroup(int groupId, Relationship r) {
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
	
	public int getGroupId() {
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
		//All relationships in this group must also conform to this groupId
		for (Relationship r : relationships) {
			r.setGroupId(groupId);
		}
	}
	
	public void addIssue (Concept c) {
		if (issues == null) {
			issues = new ArrayList<>();
		}
		issues.add(c);
	}
	
	public void addRelationship (Relationship r) {
		relationships.add(r);
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
	
	public void resetIndicators() {
		this.indicators = "";
	}
	
	@Override
	public boolean equals (Object other) {
		if (!(other instanceof RelationshipGroup)) {
			return false;
		}
		//Groups will be compared by triples, but not group id
		RelationshipGroup otherGroup = (RelationshipGroup) other;
		
		//If the count if different, we don't need to check individual items.
		if (this.getRelationships().size() != otherGroup.getRelationships().size()) {
			return false;
		}
		
		nextLhsRel:
		for (Relationship lhs : this.getRelationships()) {
			//Can we find a matching relationship.  We're sure of source, so just check type and target
			for (Relationship rhs : otherGroup.getRelationships()) {
				if (lhs.getType().equals(rhs.getType()) && lhs.getTarget().equals(rhs.getTarget())) {
					continue nextLhsRel;
				}
			}
			return false;
		}
		return true;
	}

	public boolean isGrouped() {
		return groupId > 0;
	}

	public boolean containsTypeValue(Relationship r1) {
		for (Relationship r2 : relationships) {
			if (r2.equalsTypeValue(r1)) {
				return true;
			}
		}
		return false;
	}
	
	public List<Relationship> getType(Concept t) {
		return relationships.stream()
		.filter(r -> r.getType().equals(t))
		.collect(Collectors.toList());
	}
	
	public Relationship getTypeValue(Relationship r1) {
		List<Relationship> matches = new ArrayList<>();
		for (Relationship r2 : relationships) {
			if (r2.equalsTypeValue(r1)) {
				matches.add(r2);
			}
		}
		if (matches.size() == 0) {
			return null;
		} else if (matches.size() == 1) {
			return matches.get(0);
		} else {
			throw new IllegalArgumentException("More than one matching relationship found");
		}
	}
	
	public boolean containsTypeValue(Concept type, Collection<Concept> values) {
		for (Concept value : values) {
			Relationship r1 = new Relationship(type, value);
			if (containsTypeValue(r1)) {
				return true;
			}
		}
		return false;
	}

	public void removeRelationship(Relationship r) {
		relationships.remove(r);
	}

	public boolean isEmpty() {
		return relationships.isEmpty();
	}

	public int size() {
		return relationships.size();
	}

	public void setActive(boolean isActive) {
		for (Relationship r : relationships) {
			r.setActive(isActive);
		}
	}

	public void setEffectiveTime(String effectiveTime) {
		for (Relationship r : relationships) {
			r.setEffectiveTime(effectiveTime);
		}
	}

	public void setFromAxiom(boolean fromAxiom) {
		for (Relationship r : relationships) {
			r.setFromAxiom(fromAxiom);
		}
	}
}
