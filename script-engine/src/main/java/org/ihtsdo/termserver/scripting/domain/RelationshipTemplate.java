package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;

public class RelationshipTemplate implements IRelationshipTemplate {
	private Concept type;
	private Concept target;
	private CharacteristicType characteristicType;
	private Object value;

	public RelationshipTemplate (Concept type, Concept target, CharacteristicType characteristicType) {
		this.type = type;
		this.target = target;
		this.characteristicType = characteristicType;
	}
	
	public RelationshipTemplate (Concept type, Concept target) {
		this.type = type;
		this.target = target;
		this.characteristicType = CharacteristicType.STATED_RELATIONSHIP;
	}
	
	public RelationshipTemplate(CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
	}

	public Concept getType() {
		return type;
	}
	public void setType(Concept type) {
		this.type = type;
	}
	public Concept getTarget() {
		return target;
	}
	public void setTarget(Concept target) {
		this.target = target;
	}
	public CharacteristicType getCharacteristicType() {
		return characteristicType;
	}
	public void setCharacteristicType(CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public boolean equalsTypeAndTargetValue(IRelationshipTemplate rel) {
		if (this.type.equals(rel.getType()) && this.target.equals(rel.getTarget())) {
			return true;
		}
		return false;
	}
	
	public Relationship createRelationship (Concept source, int groupId, String sctid) {
		Relationship r = new Relationship(source, type, target, groupId);
		r.setRelationshipId(sctid);
		r.setActive(true);
		r.setModuleId(source.getModuleId());
		r.setCharacteristicType(characteristicType);
		r.setDirty();
		return r;
	}
	
	public String toString() {
		return type + " -> " + target;
	}

	public Relationship toRelationship(Relationship cloneMe, String sctid) {
		Relationship r = cloneMe.clone(sctid);
		r.setActive(true);
		r.setType(type);
		r.setTarget(target);
		r.setDirty();
		return r;
	}

	@Override
	public boolean isConcrete() {
		return value != null;
	}
}
