package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;

public interface IRelationshipTemplate {


	public Concept getType();
	public void setType(Concept type);
	
	public Concept getTarget();
	public void setTarget(Concept target);
	
	public CharacteristicType getCharacteristicType();
	public void setCharacteristicType(CharacteristicType characteristicType);

	public boolean equalsTypeAndTargetValue(Relationship rel);
	
	public String toString();

}
