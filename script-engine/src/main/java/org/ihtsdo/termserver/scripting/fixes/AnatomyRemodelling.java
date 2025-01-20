package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;

/*
	Makes modifications to anatomy relationships, driven by an input CSV file
	See INFRA-1484
*/
public class AnatomyRemodelling extends BatchFix implements ScriptConstants{
	
	Map<String, ConceptChange> conceptsToProcess = new HashMap<>();
	Set<Integer> reportedItems = new HashSet<>();
	
	protected AnatomyRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		AnatomyRemodelling fix = new AnatomyRemodelling(null);
		try {
			fix.init(args);
			fix.inputFileHasHeaderRow = true;
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = 0;
		if (!loadedConcept.isActiveSafely()) {
			report(t, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive.  No changes attempted");
		} else {
			changesMade = remodelRelationships(t, concept, loadedConcept, false);
			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
		}
		return changesMade;
	}

	private int remodelRelationships(Task task, Concept remodelledConcept, Concept tsConcept, boolean trialRun) throws TermServerScriptException {
		ConceptChange changeConcept = (ConceptChange)remodelledConcept;
		int changesMade = 0;
		//Loop through the changing relationships in the change concept
		for (Relationship changingRelationship : changeConcept.getRelationships()) {
			//If we are inactivating a relationship, then we expect to find one already active.  Flag warning if not
			if (!changingRelationship.isActive()) {
				Relationship inactivateMe = findStatedRelationship(changingRelationship, tsConcept, ActiveState.ACTIVE);
				if (inactivateMe == null) {
						String msg = "Did not find expected active relationship to inactivate: " + changingRelationship.toString();
						report(task, tsConcept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
				} else {
					//We'll inactivate this relationship
					if (!trialRun) {
						inactivateMe.setActive(false);
						report(task, tsConcept, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, changingRelationship.toString());
					}
					changesMade++;
				}
			} else {
				//We're adding a new relationship.   Ensure it doesn't already exist, then see
				//if an inactive one exists that we could reactivate.  Otherwise add new.
				Relationship exisitingActive =  findStatedRelationship(changingRelationship, tsConcept, ActiveState.ACTIVE);
				Relationship exisitingInactive =  findStatedRelationship(changingRelationship, tsConcept, ActiveState.INACTIVE);
				if (exisitingActive != null) {
					report(task, tsConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "New relationship already exists: " + changingRelationship);
				} else if (exisitingInactive != null){
					if (!trialRun) {
						exisitingInactive.setActive(true);
						report(task, tsConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, "Re-activated existing relationship: " + changingRelationship);
					}
					changesMade++;
				} else {
					//Add the new relationship
					if (!trialRun) {
						tsConcept.addRelationship(changingRelationship);
						report(task, tsConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Added new relationship: " + changingRelationship);
					}
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private Relationship findStatedRelationship(
			Relationship exampleRel, Concept c,
			ActiveState active) {
		//Find the first relationship matching the type, target and activeState
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, exampleRel.getType(),  active)) {
			if (r.getTarget().equals(exampleRel.getTarget())) {
				return r;
			}
		}
		return null;
	}

	/*
	FileFormat: sub_sctid	sub_sctfsn	supe_sctid	supe_sctfsn	resource
	Where the resource is OWL we will add a relationship.
	Where the resource is RF2 will inactivate an expected existing relationship
	 */
	@Override
	protected List<Component> loadLine(String[] items) throws TermServerScriptException {

		String sctId = items[0];
		ConceptChange concept;
		//Have we seen a row for this concept before?
		if (conceptsToProcess.containsKey(sctId)) {
			concept = conceptsToProcess.get(sctId);
		} else {
			concept = new ConceptChange(sctId);
			conceptsToProcess.put(sctId, concept);
		}
		concept.setConceptType(ConceptType.ANATOMY);
		concept.setFsn(items[1]);
		if (items[2].equals("NULL")) {
			concept.setSkipReason("Target SCTID was specified as NULL");
		} else {
			Concept target = gl.getConcept(items[2]);
			if (!target.getFsn().equals(items[3])) {
				String msg = "Relationship defined for " + sctId + " |" + concept.getFsn() + "| - ";
				msg += "Stated target FSN" + items[2] + "|" + items[3] + "| does not match actual FSN |" + target.getFsn() + "|";
				throw new TermServerScriptException(msg);
			}
			
			Relationship relationship = createNewStatedRelationship(concept, IS_A, target, 0);
			String resource = items[4];
			if (resource.equals("OWL")) {
				relationship.setActive(true);
			} else if (resource.equals("RF2")) {
				relationship.setActive(false);
			} else {
				throw new TermServerScriptException("Unexpected resource: " + resource);
			}
			
			//Check for contradictory relationship ie if the change concept already has one of these in the opposite state
			ActiveState contradictoryState = relationship.isActive() ? ActiveState.INACTIVE : ActiveState.ACTIVE;
			Relationship contradiction = findStatedRelationship(relationship, concept, contradictoryState);
			if (contradiction != null) {
				concept.setSkipReason("Input file contains contradictory state for relationship: " + relationship);
			} else {
				concept.addRelationship(relationship);
				
				//Now check if we do in fact have any changes to make for this concept via a trial run
				Concept fullConcept = gl.getConcept(sctId);
				if (remodelRelationships(null, concept, fullConcept, true) == 0) {
					concept.setSkipReason("No change apparently required");
				} else {
					//We might have set a skip reason on a previous line.  Reset if this is no longer the case
					concept.setSkipReason(null);
				}
			}
		}
		return Collections.singletonList(concept);
	}

	private Relationship createNewStatedRelationship(ConceptChange source,
			Concept type, Concept target, int groupNum) {
		Relationship r = new Relationship(source, type, target, groupNum);
		r.setModuleId(SCTID_CORE_MODULE);
		r.setModifier(Modifier.EXISTENTIAL);
		r.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		return r;
	}

	public void report(Task task, Component concept, Severity severity, ReportActionType actionType, String... details) throws TermServerScriptException {
		//Keep a running list of items reported, which we might otherwise repeat due to the trial run
		String taskStr = (task == null)?"Null":task.toString();
		String concatonatedLine = taskStr + concept + severity + actionType + details[0];
		if (!reportedItems.contains(concatonatedLine.hashCode())) {
			super.report(task, concept, severity, actionType, details[0]);
			reportedItems.add(concatonatedLine.hashCode());
		}
	}
}
