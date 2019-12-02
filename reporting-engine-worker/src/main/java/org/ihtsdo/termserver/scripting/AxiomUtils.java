package org.ihtsdo.termserver.scripting;

import java.util.*;
import java.util.Map.Entry;

//import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

public class AxiomUtils {

	public static List<org.ihtsdo.termserver.scripting.domain.Relationship> getRHSRelationships(Concept c, AxiomRepresentation axiom) throws TermServerScriptException {
		GraphLoader gl = GraphLoader.getGraphLoader();
		List<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new ArrayList<>();
		if (axiom.getRightHandSideRelationships() != null) {
			for (Entry<Integer, List<Relationship>> entry : axiom.getRightHandSideRelationships().entrySet()) {
				int groupId = entry.getKey();
				for (Relationship axiomRelationship : entry.getValue()) {
					relationships.add(new org.ihtsdo.termserver.scripting.domain.Relationship (
							c,
							gl.getConcept(axiomRelationship.getTypeId()),
							gl.getConcept(axiomRelationship.getDestinationId()),
							groupId));
				}
			}
		} else {
			//Could be a GCI
			//TermServerScript.warn ("What is this? " + axiom);
		}
		return relationships;
	}
	
	public static List<org.ihtsdo.termserver.scripting.domain.Relationship> getLHSRelationships(Concept c, AxiomRepresentation axiom) throws TermServerScriptException {
		GraphLoader gl = GraphLoader.getGraphLoader();
		List<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new ArrayList<>();
		if (axiom.getLeftHandSideRelationships() != null) {
			for (Entry<Integer, List<Relationship>> entry : axiom.getLeftHandSideRelationships().entrySet()) {
				int groupId = entry.getKey();
				for (Relationship axiomRelationship : entry.getValue()) {
					relationships.add(new org.ihtsdo.termserver.scripting.domain.Relationship (
							c,
							gl.getConcept(axiomRelationship.getTypeId()),
							gl.getConcept(axiomRelationship.getDestinationId()),
							groupId));
				}
			}
		} else {
			//Could be a GCI
			//TermServerScript.warn ("What is this? " + axiom);
		}
		return relationships;
	}

}
