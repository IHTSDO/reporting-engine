package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

public abstract class Expressable extends Component implements ScriptConstants {

	public abstract DefinitionStatus getDefinitionStatus();
	
	public abstract Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType);

	public abstract Set<Relationship> getRelationships(CharacteristicType charType, ActiveState active);
	
	public abstract Set<Relationship> getRelationships(CharacteristicType charType, Concept type, ActiveState active);

	public abstract Collection<Concept> getParents(CharacteristicType charType);
	
	public String toExpression(CharacteristicType charType) {
		//Inactive concepts do not have expressions
		if (!isActive()) {
			return "Concept Inactive";
		}
		String expression = getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) ? "=== " : "<<< ";
		
		//Parents may not be maintained if we're working with a loaded concept.
		//Work with active IS_A relationships instead
		expression += getRelationships(charType, IS_A, ActiveState.ACTIVE).stream()
				.map(r -> r.getTarget())
				.map(p -> p.toString())
				.collect(Collectors.joining (" + \n"));
		
		if (getRelationships(charType, ActiveState.ACTIVE).size() > 0) {
			expression += " : \n";
		}
		//Add any ungrouped attributes
		boolean isFirstGroup = true;
		for (RelationshipGroup group : getRelationshipGroups(charType)) {
			if (isFirstGroup) {
				isFirstGroup = false;
			} else {
				expression += ",\n";
			}
			expression += group.isGrouped() ? "{" : "";
			expression += group.getRelationships().stream()
					.map(r -> "  " + r.toString())
					.sorted()
					.collect(Collectors.joining (",\n"));
			expression += group.isGrouped() ? " }" : "";
		}
		return expression;
	}
	
}
