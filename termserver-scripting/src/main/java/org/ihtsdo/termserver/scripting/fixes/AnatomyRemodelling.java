package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/*
	Makes modifications to anatomy relationships, driven by an input CSV file
	See INFRA-1484
*/
public class AnatomyRemodelling extends BatchFix implements RF2Constants{
	
	
	String[] author_reviewer = new String[] {targetAuthor};
	Map<String, ConceptChange> conceptsToProcess = new HashMap<String, ConceptChange>();
	
	protected AnatomyRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		AnatomyRemodelling fix = new AnatomyRemodelling(null);
		try {
			IS_A.setFsn("Is a");
			fix.init(args);
			fix.inputFileDelimiter = TAB;
			fix.inputFileHasHeaderRow = true;
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.startTimer();
			println ("Processing started.  See results: " + fix.reportFile.getAbsolutePath());
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (tsConcept.isActive() == false) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive.  No changes attempted");
		} else {
			changesMade = remodelRelationships(task, concept, tsConcept);
			if (changesMade > 0) {
				try {
					String conceptSerialised = gson.toJson(tsConcept);
					debug ((dryRun?"Dry run updating":"Updating") + " state of " + tsConcept + info);
					if (!dryRun) {
						tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
					}
					//report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept successfully remodelled. " + changesMade + " changes made.");
				} catch (Exception e) {
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getClass().getSimpleName()  + " - " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
		return changesMade;
	}

	private int remodelRelationships(Task task, Concept remodelledConcept, Concept tsConcept) throws TermServerScriptException {
		ConceptChange changeConcept = (ConceptChange)remodelledConcept;
		int changesMade = 0;
		//Loop through the changing relationships in the change concept
		for (Relationship changingRelationship : changeConcept.getRelationships()) {
			//If we are inactivating a relationship, then we expect to find one already active.  Flag warning if not
			if (!changingRelationship.isActive()) {
				Relationship inactivateMe = findStatedRelationship(changingRelationship, tsConcept, ActiveState.ACTIVE);
				if (inactivateMe == null) {
					String msg = "Did not find expected active relationship to inactivate: " + changingRelationship.toShortString();
					report(task, tsConcept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
				} else {
					//We'll inactivate this relationship
					inactivateMe.setActive(false);
					report(task, tsConcept, Severity.LOW, ReportActionType.RELATIONSHIP_REMOVED, changingRelationship.toShortString());
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
					exisitingInactive.setActive(true);
					report(task, tsConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, "Re-activated existing relationship: " + changingRelationship);
					changesMade++;
				} else {
					//Add the new relationship
					tsConcept.addRelationship(changingRelationship);
					report(task, tsConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Added new relationship: " + changingRelationship);
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

	@Override
	protected Batch formIntoBatch (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerScriptException {
		Batch batch = new Batch(getReportName());
		Task task = batch.addNewTask();

		for (Concept thisConcept : allConcepts) {
			ConceptChange thisConceptChange = (ConceptChange) thisConcept;
			if (thisConceptChange.getSkipReason() != null) {
				report(task, thisConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept marked as: " + thisConceptChange.getSkipReason());
			} else {
				if (task.size() >= taskSize) {
					task = batch.addNewTask();
					setAuthorReviewer(task, author_reviewer);
				}
				task.add(thisConcept);
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConcepts);
		return batch;
	}


	/*
	FileFormat: sub_sctid	sub_sctfsn	supe_sctid	supe_sctfsn	resource
	Where the resource is OWL we will inactivate the relationship
	Where the resource is RF2 will will add a relationship.
	 */
	@Override
	protected Concept loadLine(String[] items) throws TermServerScriptException {

		String sctid = items[0];
		ConceptChange concept;
		//Have we seen a row for this concept before?
		if (conceptsToProcess.containsKey(sctid)) {
			concept = conceptsToProcess.get(sctid);
		} else {
			concept = new ConceptChange(sctid);
			conceptsToProcess.put(sctid, concept);
		}
		concept.setConceptType(ConceptType.ANATOMY);
		concept.setFsn(items[1]);
		if (items[2].equals("NULL")) {
			concept.setSkipReason("Target SCTID was specified as NULL");
		} else {
			Concept target = gl.getConcept(items[2]);
			if (!target.getFsn().equals(items[3])) {
				String msg = "Relationship defined for " + sctid + " |" + concept.getFsn() + "| - ";
				msg += "Stated target FSN" + items[2] + "|" + items[3] + "| does not match actual FSN |" + target.getFsn() + "|";
				throw new TermServerScriptException(msg);
			}
			
			Relationship relationship = createNewStatedRelationship(concept, IS_A, target, 0);
			String resource = items[4];
			if (resource.equals("OWL")) {
				relationship.setActive(false);
			} else if (resource.equals("RF2")) {
				relationship.setActive(true);
			} else {
				throw new TermServerScriptException("Unexpected resource: " + resource);
			}
			concept.addRelationship(relationship);
		}
		return concept;
	}

	private Relationship createNewStatedRelationship(ConceptChange source,
			Concept type, Concept target, int groupNum) {
		Relationship r = new Relationship(source, type, target, groupNum);
		r.setModuleId(SCTID_CORE_MODULE);
		r.setModifier(Modifier.EXISTENTIAL);
		r.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		return r;
	}


}
