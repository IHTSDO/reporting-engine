package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
For APDS-335
Replace all active attributes using 704318007 |Property type (attribute)| 
with 370130000 |Property (attribute)|
*/
public class ReplaceRelationship extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	Concept findAttribute;
	Concept replaceAttribute;
	
	protected ReplaceRelationship(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceRelationship fix = new ReplaceRelationship(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			//We won't incude the project export in our timings
			fix.startTimer();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		
		//Populate our attributes of interest
		findAttribute = gl.getConcept("704318007");  // |Property type (attribute)| 
		replaceAttribute = gl.getConcept("370130000"); //|Property (attribute)|
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = replaceTargetRelationship(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int replaceTargetRelationship(Task task, Concept loadedConcept) {
		int changesMade = 0;
		for (Relationship r : loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(findAttribute)) {
				//Clone r and modify
				Relationship replacement = r.clone();
				replacement.setType(replaceAttribute);
				r.setActive(false); 
				//Do we already have a replacement attached, but inactive?
				Relationship alreadyExists = checkForInactiveRel(loadedConcept, replacement);
				if (alreadyExists != null) {
					report (task, loadedConcept, Severity.HIGH, ReportActionType.RELATIONSHIP_MODIFIED, "Reactivated existing relationship: " + alreadyExists );
					alreadyExists.setActive(true);
				} else {
					report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Replaced relationship: " + r );
					loadedConcept.getRelationships().add(replacement);
				}
				changesMade++;
			}
		}
		return changesMade;
	}

	private Relationship checkForInactiveRel(Concept concept, Relationship stated) {
		Relationship inactiveStated = null;
		for (Relationship potentialReactivation : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE)) {
			if (potentialReactivation.equals(stated)) {
				inactiveStated = potentialReactivation;
			}
		}
		return inactiveStated;
	}

	protected Batch formIntoBatch() throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask();
		Set<Concept> allConceptsBeingProcessed = identifyConceptsToProcess();

		for (Concept thisConcept : allConceptsBeingProcessed) {
			if (task.size() >= taskSize) {
				task = batch.addNewTask();
				setAuthorReviewer(task, author_reviewer);
			}
			task.add(thisConcept);
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConceptsBeingProcessed);
		return batch;
	}

	private Set<Concept> identifyConceptsToProcess() throws TermServerScriptException {
		Collection<Concept> allPotential = gl.getAllConcepts();
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : allPotential) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(findAttribute)) {
					allAffected.add(c);
					break;
				}
			}
		}
		return allAffected;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerScriptException {
		throw new NotImplementedException();
	}
}
