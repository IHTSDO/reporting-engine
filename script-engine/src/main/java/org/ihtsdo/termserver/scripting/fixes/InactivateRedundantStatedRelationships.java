package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * SUBST-230
Inactivates stated relationships where a more specific relationship exists
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateRedundantStatedRelationships extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateRedundantStatedRelationships.class);

	protected InactivateRedundantStatedRelationships(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateRedundantStatedRelationships fix = new InactivateRedundantStatedRelationships(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = inactivateRedundantStatedRelationships(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int inactivateRedundantStatedRelationships(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> activeISAs = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		for (Relationship moreSpecificISA : activeISAs) {
			//Do we have another IS A that is parent of this relationship?  Inactivate it if so.
			for (Relationship lessSpecificISA : activeISAs) {
				if (moreSpecificISA.equals(lessSpecificISA) || !lessSpecificISA.isActive()) {
					continue; //Skip self or already processed
				}
				//Need the locally loaded concept to work out ancestors
				Concept target = gl.getConcept( moreSpecificISA.getTarget().getConceptId());
				Set<Concept> ancestors = target.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, false);
				if (ancestors.contains(lessSpecificISA.getTarget())) {
					//Are we inactivating an unpublished relationship?   Must warn user to delete if so.
					if (lessSpecificISA.getEffectiveTime() == null || lessSpecificISA.getEffectiveTime().isEmpty()) {
						report(task,loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, "Deleting parent " + lessSpecificISA.getTarget() + " in favour of " + moreSpecificISA.getTarget());
						loadedConcept.removeRelationship(lessSpecificISA);
					} else {
						lessSpecificISA.setActive(false);
						report(task,loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, "Inactivating parent " + lessSpecificISA.getTarget() + " in favour of " + moreSpecificISA.getTarget());
					}
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		print ("Processing concepts to look for redundant IS A relationships");
		for (Concept concept :gl.getAllConcepts()) {
			if (concept.isActive()) {
				//We're working with concepts which have multiple stated parents.
				if (concept.getParents(CharacteristicType.STATED_RELATIONSHIP).size() > 1) {
					Set<Relationship> activeISAs = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
					for (Relationship moreSpecificISA : activeISAs) {
						//Do we have another IS A that is parent of this relationship?  Inactivate it if so.
						for (Relationship lessSpecificISA : activeISAs) {
							if (moreSpecificISA.equals(lessSpecificISA) || !lessSpecificISA.isActive()) {
								continue; //Skip self or already processed
							}
							Set<Concept> ancestors = moreSpecificISA.getTarget().getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, false);
							if (ancestors.contains(lessSpecificISA.getTarget())) {
								processMe.add(concept);
							}
						}
					}
				}
			}
		}
		LOGGER.debug("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
