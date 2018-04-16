package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
 * SUBST-230
Inactivates stated relationships where a more specific relationship exists
 */
public class InactivateRedundantStatedRelationships extends BatchFix implements RF2Constants{
	
	
	protected InactivateRedundantStatedRelationships(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		InactivateRedundantStatedRelationships fix = new InactivateRedundantStatedRelationships(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't incude the project export in our timings
			fix.startTimer();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			info ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = inactivateRedundantStatedRelationships(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int inactivateRedundantStatedRelationships(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		List<Relationship> activeISAs = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		for (Relationship moreSpecificISA : activeISAs) {
			//Do we have another IS A that is parent of this relationship?  Inactivate it if so.
			for (Relationship lessSpecificISA : activeISAs) {
				if (moreSpecificISA.equals(lessSpecificISA) || !lessSpecificISA.isActive()) {
					continue; //Skip self or already processed
				}
				//Need the locally loaded concept to work out ancestors
				Concept target = gl.getConcept( moreSpecificISA.getTarget().getConceptId());
				Set<Concept> ancestors = target.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false);
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
					List<Relationship> activeISAs = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
					for (Relationship moreSpecificISA : activeISAs) {
						//Do we have another IS A that is parent of this relationship?  Inactivate it if so.
						for (Relationship lessSpecificISA : activeISAs) {
							if (moreSpecificISA.equals(lessSpecificISA) || !lessSpecificISA.isActive()) {
								continue; //Skip self or already processed
							}
							Set<Concept> ancestors = moreSpecificISA.getTarget().getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false);
							if (ancestors.contains(lessSpecificISA.getTarget())) {
								processMe.add(concept);
							}
						}
					}
				}
			}
		}
		debug ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
