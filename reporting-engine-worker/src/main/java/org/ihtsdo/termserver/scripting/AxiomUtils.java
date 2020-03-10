package org.ihtsdo.termserver.scripting;

import java.util.*;
import java.util.Map.Entry;

//import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Axiom;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

public class AxiomUtils {

	public static List<org.ihtsdo.termserver.scripting.domain.Relationship> getRHSRelationships(Concept c, AxiomRepresentation axiom) throws TermServerScriptException {
		List<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new ArrayList<>();
		if (axiom.getRightHandSideRelationships() != null) {
			relationships = convertMapToRelationship(c, axiom.getRightHandSideRelationships());
		} else {
			//Could be a GCI
			//TermServerScript.warn ("What is this? " + axiom);
		}
		return relationships;
	}
	
	public static List<org.ihtsdo.termserver.scripting.domain.Relationship> getLHSRelationships(Concept c, AxiomRepresentation axiom) throws TermServerScriptException {
		List<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new ArrayList<>();
		if (axiom.getLeftHandSideRelationships() != null) {
			relationships = convertMapToRelationship(c, axiom.getLeftHandSideRelationships());
		} else {
			//Could be a GCI
			//TermServerScript.warn ("What is this? " + axiom);
		}
		return relationships;
	}

	public static Axiom toAxiom(Concept c, AxiomEntry axiomEntry, AxiomRepresentation axiomRepresentation) throws TermServerScriptException {
		Axiom axiom = new Axiom(c);
		axiom.setActive(axiomEntry.isActive());
		axiom.setModuleId(axiomEntry.getModuleId());
		axiom.setRelationships(extractRelationships(c, axiomRepresentation));
		return axiom;
	}

	private static List<org.ihtsdo.termserver.scripting.domain.Relationship> extractRelationships(
			Concept c,
			AxiomRepresentation axiomRepresentation) throws TermServerScriptException {
		Map<Integer, List<Relationship>> relationshipMap = null;
		if (hasContent(axiomRepresentation.getLeftHandSideRelationships())) {
			relationshipMap = axiomRepresentation.getLeftHandSideRelationships();
		}
		
		if (hasContent(axiomRepresentation.getRightHandSideRelationships())) {
			if (relationshipMap != null) {
				throw new IllegalArgumentException("Concept has axiom with LHS + RHS axiom modeling: " + c);
			}
			relationshipMap = axiomRepresentation.getRightHandSideRelationships();
		}
		return convertMapToRelationship(c, relationshipMap);
	}
	
	private static boolean hasContent(Map<Integer, List<Relationship>> map) {
		return map != null && !map.isEmpty();
	}

	static private List<org.ihtsdo.termserver.scripting.domain.Relationship> convertMapToRelationship(Concept c, Map<Integer, List<Relationship>> relationshipMap) throws TermServerScriptException {
		GraphLoader gl = GraphLoader.getGraphLoader();
		List<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new ArrayList<>();
		for (Entry<Integer, List<Relationship>> entry : relationshipMap.entrySet()) {
			int groupId = entry.getKey();
			for (Relationship axiomRelationship : entry.getValue()) {
				relationships.add(new org.ihtsdo.termserver.scripting.domain.Relationship (
						c,
						gl.getConcept(axiomRelationship.getTypeId()),
						gl.getConcept(axiomRelationship.getDestinationId()),
						groupId));
			}
		}
		return relationships;
	}

}
