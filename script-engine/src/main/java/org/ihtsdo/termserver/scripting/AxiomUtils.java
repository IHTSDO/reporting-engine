package org.ihtsdo.termserver.scripting;

import java.util.*;
import java.util.Map.Entry;

//import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Axiom;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

/**
 * TODO Enhance GraphLoader to read in MRCM to determine never grouped attributes
 * to pass in to axiomService constructor
 */
public class AxiomUtils implements RF2Constants {
	
	static AxiomRelationshipConversionService axiomService = new AxiomRelationshipConversionService (new HashSet<>());

	public static Set<org.ihtsdo.termserver.scripting.domain.Relationship> getRHSRelationships(Concept c, AxiomRepresentation axiom) throws TermServerScriptException {
		Set<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new HashSet<>();
		if (axiom.getRightHandSideRelationships() != null) {
			relationships = convertMapToRelationship(c, axiom.getRightHandSideRelationships());
		} else {
			//Could be a GCI
			//TermServerScript.warn ("What is this? " + axiom);
		}
		return relationships;
	}
	
	public static Set<org.ihtsdo.termserver.scripting.domain.Relationship> getLHSRelationships(Concept c, AxiomRepresentation axiom) throws TermServerScriptException {
		Set<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new HashSet<>();
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

	private static Set<org.ihtsdo.termserver.scripting.domain.Relationship> extractRelationships(
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

	static private Set<org.ihtsdo.termserver.scripting.domain.Relationship> convertMapToRelationship(Concept c, Map<Integer, List<Relationship>> relationshipMap) throws TermServerScriptException {
		GraphLoader gl = GraphLoader.getGraphLoader();
		Set<org.ihtsdo.termserver.scripting.domain.Relationship> relationships = new HashSet<>();
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
	
	static public Map<Integer, List<Relationship>> convertRelationshipsToMap(Set<org.ihtsdo.termserver.scripting.domain.Relationship> relationships) {
		Map<Integer, List<Relationship>> relationshipMap = new HashMap<>();
		for (org.ihtsdo.termserver.scripting.domain.Relationship r : relationships) {
			List<Relationship> group = relationshipMap.get(r.getGroupId());
			if (group == null) {
				group = new ArrayList<Relationship>();
				relationshipMap.put(r.getGroupId(), group);
			}
			group.add(toRelationship(r));
		}
		return relationshipMap;
	}

	private static Relationship toRelationship(org.ihtsdo.termserver.scripting.domain.Relationship r) {
		return new Relationship(Long.parseLong(r.getType().getId()), 
				Long.parseLong(r.getTarget().getId()));
	}

	public static List<AxiomEntry> convertClassAxiomsToAxiomEntries(Concept c) {
		List<AxiomEntry> axiomEntries = new ArrayList<>();
		for (Axiom axiom : c.getClassAxioms()) {
			AxiomEntry a = new AxiomEntry();
			a.setId(UUID.randomUUID().toString());
			a.setEffectiveTime(axiom.getEffectiveTime());
			a.setActive(true);
			a.setModuleId(c.getModuleId());
			a.setReferencedComponentId(c.getId());
			a.setRefsetId(SCTID_OWL_AXIOM_REFSET);
			AxiomRepresentation axiomRep = new AxiomRepresentation();
			axiomRep.setLeftHandSideNamedConcept(Long.parseLong(c.getConceptId()));
			axiomRep.setRightHandSideRelationships(convertRelationshipsToMap(axiom.getRelationships()));
			axiomRep.setPrimitive(c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE));
			String owl = axiomService.convertRelationshipsToAxiom(axiomRep);
			a.setOwlExpression(owl);
			a.setDirty();
			axiomEntries.add(a);
		}
		return axiomEntries;
	}

}
