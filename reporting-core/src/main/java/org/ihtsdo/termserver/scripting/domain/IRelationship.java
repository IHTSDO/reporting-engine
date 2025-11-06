package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.RF2Constants;

public interface IRelationship extends RF2Constants {

	public Concept getType();
	public void setType(Concept type);
	
	public Concept getTarget();
	public void setTarget(Concept target);
	
	public ConcreteValue getConcreteValue();
	public void setConcreteValue(ConcreteValue value);
	
	public CharacteristicType getCharacteristicType();
	public void setCharacteristicType(CharacteristicType characteristicType);

	public boolean equalsTypeAndTargetValue(IRelationship rel);
	
	public String toString();
	
	public boolean isConcrete();

    void setGroupId(int groupId);

	int getGroupId();

	IRelationship clone();

    Relationship instantiate(Concept source, int groupId);

	void setDirty();
}
