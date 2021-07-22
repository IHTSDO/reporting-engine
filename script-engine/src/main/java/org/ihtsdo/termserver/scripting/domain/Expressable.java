package org.ihtsdo.termserver.scripting.domain;

import java.util.Collection;
import java.util.Set;

import org.ihtsdo.otf.RF2Constants;

public interface Expressable extends RF2Constants {

	DefinitionStatus getDefinitionStatus();
	
	Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType);

	Set<Relationship> getRelationships(CharacteristicType charType, ActiveState active);

	Collection<Concept> getParents(CharacteristicType charType);
	
}
