package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

public class RelationshipGroup {
	Set<Relationship> relationships = new HashSet<>();
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
		this.relationships = new HashSet<>();
	}
	
	public RelationshipGroup (int groupId, Set<Relationship> relationships) {
		this.groupId = groupId;
		this.relationships = relationships;
	}
	
	public RelationshipGroup(int groupId, Relationship r) {
		relationships = new HashSet<>();
		this.groupId = groupId;
		relationships.add(r);
	}

	public Set<Relationship> getRelationships() {
		return relationships;
	}
	
	public Set<Relationship> getRelationships(Concept type) {
		Set<Relationship> matching = new HashSet<>();
		for (Relationship r : relationships) {
			if (r.getType().equals(type)) {
				matching.add(r);
			}
		}
		return matching;
	}
	
	public void setRelationships(Set<Relationship> relationships) {
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
				.sorted((r1, r2) -> r1.getType().getFsnSafely().compareTo(r2.getType().getFsnSafely()))
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

	public boolean containsTypeValue(IRelationshipTemplate r1) {
		for (Relationship r2 : relationships) {
			if (r2.equalsTypeAndTargetValue(r1)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean containsType(RelationshipTemplate rt) {
		for (Relationship r2 : relationships) {
			if (r2.getType().equals(rt.getType())) {
				return true;
			}
		}
		return false;
	}
	
	public Set<Relationship> getType(Concept t) {
		return relationships.stream()
		.filter(r -> r.getType().equals(t))
		.collect(Collectors.toSet());
	}
	
	public Concept getValueForType(Concept type) {
		List<Relationship> rels = relationships.stream()
		.filter(r -> r.getType().equals(type))
		.collect(Collectors.toList());
		
		if (rels.size() != 1) {
			throw new IllegalArgumentException("Expected 1 attribute of type " + type + " found " + rels.size() + " in " + this.toString());
		}
		return rels.get(0).getTarget();
	}
	
	public Relationship getTypeValue(Relationship r1) {
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r2 : relationships) {
			if (r2.equalsTypeAndTargetValue(r1)) {
				matches.add(r2);
			}
		}
		if (matches.size() == 0) {
			return null;
		} else if (matches.size() == 1) {
			return matches.iterator().next();
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

	public void setModule(String moduleId) {
		for (Relationship r : relationships) {
			r.setModuleId(moduleId);
		}
	}

	public void setAxiom(AxiomEntry axiomEntry) {
		for (Relationship r : relationships) {
			r.setAxiomEntry(axiomEntry);
		}
	}

	public List<org.snomed.otf.owltoolkit.domain.Relationship> getToolKitRelationships() {
		List<org.snomed.otf.owltoolkit.domain.Relationship> toolkitRels = new ArrayList<>();
		for (Relationship r : getRelationships()) {
			int groupId = r.getGroupId();
			long type = Long.parseLong(r.getType().getConceptId());
			long target = Long.parseLong(r.getTarget().getConceptId());
			org.snomed.otf.owltoolkit.domain.Relationship toolKitRel = 
					new org.snomed.otf.owltoolkit.domain.Relationship(groupId, type, target);
			toolkitRels.add(toolKitRel);
		}
		return toolkitRels;
	}

	public Concept getSourceConcept() {
		//Can return any relationship's source concept - they will all be the same
		return relationships.iterator().next().getSource();
	}


}
