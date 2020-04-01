package org.ihtsdo.termserver.scripting.domain;

import java.util.Collection;
import java.util.List;

import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.DefinitionStatus;

public interface Expressable {

	DefinitionStatus getDefinitionStatus();
	
	Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType);

	List<Relationship> getRelationships(CharacteristicType charType, ActiveState active);

	Collection<Concept> getParents(CharacteristicType charType);
	
}
