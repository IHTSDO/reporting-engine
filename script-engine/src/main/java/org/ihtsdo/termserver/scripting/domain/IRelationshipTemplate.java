package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.RF2Constants;

public interface IRelationshipTemplate extends RF2Constants {


	public Concept getType();
	public void setType(Concept type);
	
	public Concept getTarget();
	public void setTarget(Concept target);
	
	public ConcreteValue getConcreteValue();
	public void setConcreteValue(ConcreteValue value);
	
	public CharacteristicType getCharacteristicType();
	public void setCharacteristicType(CharacteristicType characteristicType);

	public boolean equalsTypeAndTargetValue(IRelationshipTemplate rel);
	
	public String toString();
	
	public boolean isConcrete();

}
