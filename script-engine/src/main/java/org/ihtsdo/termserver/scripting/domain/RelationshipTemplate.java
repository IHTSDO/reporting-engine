package org.ihtsdo.termserver.scripting.domain;

public class RelationshipTemplate implements IRelationshipTemplate {
	
	public enum Mode { PERMISSIVE, REPLACE_TYPE_IN_THIS_GROUP, UNIQUE_TYPE_IN_THIS_GROUP, UNIQUE_TYPE_ACROSS_ALL_GROUPS, UNIQUE_TYPE_VALUE_ACROSS_ALL_GROUPS}
	
	private Concept type;
	private Concept target;
	private CharacteristicType characteristicType;
	private ConcreteValue concreteValue;
	private Mode mode;

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
	
	public RelationshipTemplate (Concept type, Concept target, Mode mode) {
		this(type, target);
		this.mode = mode;
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

	public ConcreteValue getConcreteValue() {
		return concreteValue;
	}

	public void setConcreteValue(ConcreteValue concreteValue) {
		this.concreteValue = concreteValue;
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
		return concreteValue != null;
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}
}
