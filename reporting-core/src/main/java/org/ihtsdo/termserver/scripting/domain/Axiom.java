package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentStore;

public class Axiom extends Expressable implements ScriptConstants {

	@SerializedName(value="axiomId", alternate="id")
	@Expose
	private String axiomId;
	@SerializedName("definitionStatus")
	@Expose
	private DefinitionStatus definitionStatus;
	@SerializedName("relationships")
	@Expose
	private Set<Relationship> relationships = new HashSet<>();
	@SerializedName("namedConceptOnLeft")
	@Expose
	private Boolean namedConceptOnLeft;

	public Axiom(Concept c) {
		moduleId = c.getConceptId();
		definitionStatus = c.getDefinitionStatus();
		active = true;
	}
	
	public String getAxiomId() {
		return axiomId;
	}
	public void setAxiomId(String axiomId) {
		this.axiomId = axiomId;
	}
	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}
	public void setDefinitionStatus(DefinitionStatus definitionStatus) {
		this.definitionStatus = definitionStatus;
	}
	public Set<Relationship> getRelationships() {
		return relationships;
	}
	public void setRelationships(Set<Relationship> relationships) {
		this.relationships = relationships;
	}
	public Boolean getNamedConceptOnLeft() {
		return namedConceptOnLeft;
	}
	public void setNamedConceptOnLeft(Boolean namedConceptOnLeft) {
		this.namedConceptOnLeft = namedConceptOnLeft;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Axiom)) {
			return false;
		}
		//Can we compare ids?
		Axiom other = (Axiom)o;
		if (this.getId() != null || other.getId() != null) {
			return this.getId().equals(other.getId());
		}
		//If neither has an Id, are they the same object?
		return this == o;
	}
	
	@Override
	public int hashCode() {
		return this.getId().hashCode();
	}
	
	@Override
	public String getId() {
		return axiomId;
	}
	@Override
	public String getReportedName() {
		throw new NotImplementedException();
	}
	@Override
	public String getReportedType() {
		throw new NotImplementedException();
	}
	@Override
	public ComponentType getComponentType() {
		return ComponentType.AXIOM;
	}
	@Override
	public String[] toRF2() throws TermServerScriptException {
		throw new NotImplementedException();
	}

	public void clearRelationships() {
		relationships.clear();
	}
	
	public String toString() {
		return toExpression(CharacteristicType.STATED_RELATIONSHIP);
	}

	public Axiom clone(String id, Concept c) {
		Axiom clone = new Axiom(c);
		clone.setAxiomId(id);
		clone.setModuleId(this.getModuleId());
		clone.setActive(this.isActive());
		for (Relationship r : this.getRelationships()) {
			Relationship rClone = r.clone();
			rClone.setSource(c);
			clone.getRelationships().add(rClone);
		}
		return clone;
	}
	
	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) {
		//Actually not expecting to see this called since an RF2 comparison
		//Would examine axionEntry objects;
		throw new IllegalStateException("Unexpected comparison of axiom");
	}
	
	public Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType, boolean includeIsA) {
		Map<Integer, RelationshipGroup> groups = new HashMap<>();
		//If we're including group 0, always add that in any event
		for (Relationship r : getRelationships()) {
			if (!includeIsA && r.getType().equals(IS_A)) {
				continue;
			}
			//Do we know about this Relationship Group yet?
			RelationshipGroup group = groups.get(r.getGroupId());
			if (group == null) {
				group = new RelationshipGroup(r.getGroupId() , r);
				groups.put(r.getGroupId(), group);
			} else {
				group.addRelationship(r);
			}
		}
		return groups.values();
	}

	@Override
	public Set<Relationship> getRelationships(CharacteristicType charType, ActiveState active) {
		return relationships;
	}

	@Override
	public Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType) {
		return getRelationshipGroups(characteristicType, false);
	}

	@Override
	public Collection<Concept> getParents(CharacteristicType charType) {
		return relationships.stream()
				.filter(r -> r.isActive())
				.filter(r -> r.getType().equals(IS_A))
				.map(r -> r.getTarget())
				.collect(Collectors.toList());
	}

	@Override
	public void setId(String id) {
		setAxiomId(id);
	}

	@Override
	public String[] getMutableFields() {
		throw new NotImplementedException("Expecting getMutableFields to be called on AxiomEntry rather than Axiom");
	}

	@Override
	public Set<Relationship> getRelationships(CharacteristicType charType, Concept type, ActiveState activeState) {
		return relationships.stream()
				.filter(r -> r.hasActiveState(activeState))
				.filter(r -> r.getType().equals(type))
				.collect(Collectors.toSet());
	}

	@Override
	public boolean matchesMutableFields(Component other) {
		throw new IllegalArgumentException("Wasn't expecting to compare Axioms!");
	}

	@Override
	public List<Component> getReferencedComponents(ComponentStore cs) {
		Set<Component> referencedComponents = new HashSet<>();
		for (Relationship r : relationships) {
			referencedComponents.addAll(r.getReferencedComponents(cs));
		}
		return new ArrayList<>(referencedComponents);
	}
}
