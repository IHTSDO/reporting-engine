package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Set the moduleId of Relationships and Axioms to the same as the source/owning Concept.
 */
public class ModuleDifferentFromSourceConcept extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModuleDifferentFromSourceConcept.class);

	enum Mode { RELATIONSHIP, AXIOM, BOTH }
	private static final Mode mode = Mode.RELATIONSHIP;
	private int updatedAxioms = 0;
	private int updatedRelationships = 0;
	
	public static void main(String[] args) throws Exception {
		new ModuleDifferentFromSourceConcept().standardExecution(args);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"Concept Id, Concept FSN, Member Id, Active, Details",
				"Concept Id, Concept FSN, Relationship Id, Active, Details",
				"Item, Count"
		};

		String[] tabNames = new String[]{
				"Stated Axioms",
				"Inferred",
				"Summary"
		};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	protected void process() throws TermServerScriptException {
		// Process concepts
		for (Concept concept : gl.getAllConcepts()) {
			if (concept.getId().equals("1029931000202109")) {
				LOGGER.debug("Debug here");
			}
			if (!concept.isActiveSafely() || !inScope(concept)) {
				continue;
			}
			
			if (mode == Mode.AXIOM || mode == Mode.BOTH) {
				alignAxioms(concept);
			}

			if (mode == Mode.RELATIONSHIP || mode == Mode.BOTH) {
				alignRelationships(concept);
			}
		}

		// Write summary
		report(TERTIARY_REPORT, "Updated Axioms", updatedAxioms);
		report(TERTIARY_REPORT, "Updated Relationships", updatedRelationships);
	}

	private void alignAxioms(Concept concept) throws TermServerScriptException {
		String conceptModuleId = concept.getModuleId();
		// Change Axioms
		for (AxiomEntry axiomEntry : concept.getAxiomEntries()) {
			String message = String.format("Axiom's moduleId changed from %s to %s", axiomEntry.getModuleId(), conceptModuleId);
			boolean sameModuleId = Objects.equals(axiomEntry.getModuleId(), conceptModuleId);
			if (!sameModuleId) {
				// Write change to RF2
				axiomEntry.setModuleId(conceptModuleId);
				axiomEntry.setEffectiveTime(null);
				writeToRF2File(owlDeltaFilename, axiomEntry.toRF2());

				// Report change
				report(0, concept.getId(), concept.getFsn(), axiomEntry.getId(), axiomEntry.isActive(), message);
				updatedAxioms++;
			}
		}
	}
	
	private void alignRelationships(Concept concept) throws TermServerScriptException {
		// Change Relationships
		String conceptModuleId = concept.getModuleId();
		Set<Relationship> relationships = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Relationship relationship : relationships) {
			boolean sameModuleId = Objects.equals(relationship.getModuleId(), conceptModuleId);
			if (!sameModuleId) {
				String message = String.format("Relationship's moduleId from %s to %s", relationship.getModuleId(), conceptModuleId);
				// Write change to RF2
				relationship.setModuleId(conceptModuleId);
				relationship.setEffectiveTime(null);
				writeToRF2File(relDeltaFilename, relationship.toRF2());

				// Report change
				report(1, concept.getId(), concept.getFsn(), relationship.getId(), relationship.isActive(), message);
				updatedRelationships++;
			}
		}
	}

}
