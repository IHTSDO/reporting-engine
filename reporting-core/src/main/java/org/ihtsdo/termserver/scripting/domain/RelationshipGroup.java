package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

public class RelationshipGroup implements ScriptConstants {
	private Set<IRelationship> relationships = new HashSet<>();
	private int groupId;
	private AxiomEntry axiomEntry;  //Used when loading from RF2
	
	//Generic flag to say if group should be highlighted for some reason, eg cause a template match to fail
	String indicators = "";
	//Generic holder to record some property of this relationship which we need to deal with
	List<Concept> issues;
	
	public RelationshipGroup clone() {
		return clone(groupId);
	}

	public RelationshipGroup clone(int newGroupId) {
		RelationshipGroup clone = new RelationshipGroup(newGroupId);
		for (IRelationship r : relationships) {
			IRelationship rClone = r.clone();
			rClone.setGroupId(newGroupId);
			clone.addRelationship(rClone);
		}
		return clone;
	}
	
	public RelationshipGroup (int groupId) {
		this.groupId = groupId;
		this.relationships = new HashSet<>();
	}
	
	public RelationshipGroup (int groupId, Set<? extends IRelationship> relationships) {
		this.groupId = groupId;
		this.relationships = new HashSet<>(relationships);
	}

	/*public RelationshipGroup (int groupId, Set<Relationship> relationships) {
		this.groupId = groupId;
		this.relationships = new HashSet<>(relationships);
	}*/
	
	public RelationshipGroup(int groupId, Relationship r) {
		relationships = new HashSet<>();
		this.groupId = groupId;
		relationships.add(r);
	}

	public Set<IRelationship> getIRelationships() {
		return relationships;
	}

	//Watch here that you're not getting the internal collection back, so you can't add to this set.
	//Use 'addRelationship' instead
	public Set<Relationship> getRelationships() {
		return relationships.stream()
				.map(r -> ensureRelationship(r))
				.collect(Collectors.toSet());
	}

	public Set<Relationship> getRelationshipsWithType(Concept type) {
		Set<Relationship> matching = new HashSet<>();
		for (IRelationship ir : relationships) {
			if (ir.getType().equals(type)) {
				matching.add(ensureRelationship(ir));
			}
		}
		return matching;
	}
	
	public Set<IRelationship> getRelationshipsWithTypeValue(Concept type, Concept value) {
		Set<IRelationship> matching = new HashSet<>();
		for (IRelationship r : relationships) {
			if (r.getType().equals(type) && r.getTarget().equals(value)) {
				matching.add(r);
			}
		}
		return matching;
	}
	
	public Relationship getRelationshipWithType(Concept type) {
		Set<Relationship> relationships = getRelationshipsWithType(type);
		if (relationships.isEmpty()) {
			return null;
		} else if (relationships.size() > 1) {
			throw new IllegalStateException(this + " features multiple " + type);
		}
		return ensureRelationship(relationships.iterator().next());
	}
	
	public Relationship getRelationshipWithTypeValue(Concept type, Concept value) {
		Set<IRelationship> relationships = getRelationshipsWithTypeValue(type, value);
		if (relationships.isEmpty()) {
			return null;
		} else if (relationships.size() > 1) {
			throw new IllegalStateException(this + " features multiple " + type);
		}
		return ensureRelationship(relationships.iterator().next());
	}
	
	public Set<Relationship> getRelationships(ActiveState activeState) {
		return relationships.stream()
				.map(ir -> ensureRelationship(ir))
				.filter(r -> r.hasActiveState(activeState))
				.collect(Collectors.toSet());
	}
	
	public void setRelationships(Set<IRelationship> relationships) {
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
		for (IRelationship r : relationships) {
			r.setGroupId(groupId);
		}
	}

	public void setDirty() {
		for (IRelationship r : relationships) {
			r.setDirty();
		}
	}
	
	public void addIssue (Concept c) {
		if (issues == null) {
			issues = new ArrayList<>();
		}
		issues.add(c);
	}
	
	public void addRelationship (IRelationship r) {
		r.setGroupId(groupId);
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
		for (IRelationship lhs : this.getRelationships()) {
			//Can we find a matching relationship.  We're sure of source, so just check type and target
			for (IRelationship rhs : otherGroup.getRelationships()) {
				//If one is concrete and the other is not, then they cannot be equal
				if ((lhs.isConcrete() && !rhs.isConcrete()) ||
						!lhs.isConcrete() && rhs.isConcrete()) {
					continue;
				}
				if ((!lhs.isConcrete() && lhs.getTarget() == null) ||
					(!rhs.isConcrete() && rhs.getTarget() == null)){
					throw new IllegalArgumentException("Null target in non concrete relationship" + lhs);
				}

				if ((lhs.isConcrete() && rhs.isConcrete()) &&
						lhs.getType().equals(rhs.getType()) && lhs.getConcreteValue().equals(rhs.getConcreteValue())) {
					continue nextLhsRel;
				} else if ((!lhs.isConcrete() && !rhs.isConcrete()) &&
						lhs.getType().equals(rhs.getType()) && lhs.getTarget().equals(rhs.getTarget())) {
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

	public boolean containsTypeValue(IRelationship r1) {
		for (IRelationship r2 : relationships) {
			if (r2.equalsTypeAndTargetValue(r1)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean containsType(RelationshipTemplate rt) {
		return containsType(rt.getType());
	}

	public boolean containsType(Concept type) {
		for (IRelationship r2 : relationships) {
			if (r2.getType().equals(type)) {
				return true;
			}
		}
		return false;
	}
	
	public Set<Relationship> getType(Concept t) {
		return relationships.stream()
		.filter(r -> r.getType().equals(t))
				.map(r -> ensureRelationship(r))
		.collect(Collectors.toSet());
	}

	public Set<IRelationship> getIRelationshipWithType(Concept t) {
		return relationships.stream()
				.filter(r -> r.getType().equals(t))
				.collect(Collectors.toSet());
	}
	
	public Concept getValueForType(Concept type) {
		return getValueForType(type, false);
	}
	
	public Concept getValueForType(Concept type, boolean allowNull) {
		List<IRelationship> rels = relationships.stream()
		.filter(r -> r.getType().equals(type))
		.collect(Collectors.toList());
		
		if (rels.size() != 1) {
			if (rels.size() == 0 && allowNull) {
				return null;
			}
			throw new IllegalArgumentException("Expected 1 attribute of type " + type + " found " + rels.size() + " in " + this.toString());
		}
		return rels.get(0).getTarget();
	}

	public Relationship getTypeValue(Relationship r1) {
		return ensureRelationship(getTypeValueIRelationship(r1));
	}
	
	public IRelationship getTypeValueIRelationship(Relationship r1) {
		Set<IRelationship> matches = new HashSet<>();
		for (IRelationship r2 : relationships) {
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
	
	public boolean containsTypeValue(Concept type, Concept value) {
		Relationship r = new Relationship(type, value);
		return containsTypeValue(r);
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
		for (IRelationship ir : relationships) {
			Relationship r = ensureRelationship(ir);
			r.setActive(isActive);
		}
	}

	private Relationship ensureRelationship(IRelationship ir) {
		if (ir instanceof Relationship) {
			return (Relationship)ir;
		}
		throw new IllegalArgumentException("Expected a Relationship, found " + ir.getClass().getSimpleName() + " " + ir);
	}

	public void setEffectiveTime(String effectiveTime) {
		for (IRelationship ir : relationships) {
			Relationship r = ensureRelationship(ir);
			r.setEffectiveTime(effectiveTime);
		}
	}

	public void setModule(String moduleId) {
		for (IRelationship ir : relationships) {
			Relationship r = ensureRelationship(ir);
			r.setModuleId(moduleId);
		}
	}

	public void setAxiom(AxiomEntry axiomEntry) {
		for (IRelationship ir : relationships) {
			Relationship r = ensureRelationship(ir);
			r.setAxiomEntry(axiomEntry);
		}
	}

	public List<org.snomed.otf.owltoolkit.domain.Relationship> getToolKitRelationships() {
		List<org.snomed.otf.owltoolkit.domain.Relationship> toolkitRels = new ArrayList<>();
		for (IRelationship r : getIRelationships()) {
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
		return ensureRelationship(relationships.iterator().next()).getSource();
	}

	public void setReleased(Boolean isReleased) {
		for (IRelationship r : relationships) {
			ensureRelationship(r).setReleased(isReleased);
		}
	}

	public AxiomEntry getAxiomEntry() {
		if (axiomEntry == null) {
			//Use the first axiom entry assigned
			for (IRelationship ir : getIRelationships()) {
				Relationship r = ensureRelationship(ir);
				if (r.getAxiomEntry() != null) {
					axiomEntry = r.getAxiomEntry();
					break;
				}
			}
		}
		return axiomEntry;
	}

	public void setAxiomEntry(AxiomEntry axiomEntry) {
		this.axiomEntry = axiomEntry;
	}

	public boolean isAllISA() {
		for (IRelationship r : getIRelationships()) {
			if (!r.getType().equals(IS_A)) {
				return false;
			}
		}
		return true;
	}

}
