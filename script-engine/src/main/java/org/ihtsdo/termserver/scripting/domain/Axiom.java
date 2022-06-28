package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Axiom extends Component implements ScriptConstants, Expressable {

	@SerializedName(value="axiomId", alternate="id")
	@Expose
	private String axiomId;
	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private Boolean active;
	@SerializedName("released")
	@Expose
	private Boolean released;
	@SerializedName("definitionStatus")
	@Expose
	private DefinitionStatus definitionStatus;
	@SerializedName("relationships")
	@Expose
	private Set<Relationship> relationships = new HashSet<Relationship>();
	@SerializedName("namedConceptOnLeft")
	@Expose
	private Boolean namedConceptOnLeft;

	public Axiom(Concept c) {
		moduleId = c.getConceptId();
		definitionStatus = c.getDefinitionStatus();
		active = true;
	}
	
	public String getModuleId() {
		return moduleId;
	}
	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}
	public Boolean getReleased() {
		return released;
	}
	public void setReleased(Boolean released) {
		this.released = released;
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
	public String getEffectiveTime() {
		return this.effectiveTime;
	}
	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}
	public void setActive(boolean newActiveState) {
		if (this.active != null && !this.active == newActiveState) {
			this.effectiveTime = null;
			setDirty();
		}
		this.active = newActiveState;
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
	public boolean isActive() {
		return active;
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
		return SnomedUtils.getModel(this, null);
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
				group.getRelationships().add(r);
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
	public Boolean isReleased() {
		return released;
	}

	@Override
	public void setId(String id) {
		setAxiomId(id);
	}

	@Override
	public String getMutableFields() {
		throw new NotImplementedException("Expecting getMutableFields to be called on AxiomEntry rather than Axiom");
	}
}
