package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Axiom extends Component implements RF2Constants, Expressable {

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
	private List<Relationship> relationships = new ArrayList<Relationship>();
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
	public List<Relationship> getRelationships() {
		return relationships;
	}
	public void setRelationships(List<Relationship> relationships) {
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
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public boolean isActive() {
		return active;
	}
	@Override
	public String getReportedName() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String getReportedType() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public ComponentType getComponentType() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String[] toRF2() throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

	public void clearRelationships() {
		relationships = new ArrayList<>();
	}
	
	public String toString() {
		return SnomedUtils.getModel(this, null);
	}

	public Axiom clone(String id, Concept c) {
		Axiom clone = new Axiom(c);
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
	public List<String> fieldComparison(Component other) {
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
	public List<Relationship> getRelationships(CharacteristicType charType, ActiveState active) {
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
}
