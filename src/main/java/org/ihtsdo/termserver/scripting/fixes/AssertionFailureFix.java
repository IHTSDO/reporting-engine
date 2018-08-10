package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
Assertion Failure fix checks a number of known assertion issues and makes
changes to the concepts if required
 */
public class AssertionFailureFix extends BatchFix implements RF2Constants{
	
	protected AssertionFailureFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		AssertionFailureFix fix = new AssertionFailureFix(null);
		try {
			fix.useAuthenticatedCookie = true;  //MS Servers have been update to use personal logins
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); //Load FSNs only
			//We won't incude the project export in our timings
			fix.startTimer();
			fix.processFile();
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
		int changesMade = fixAssertionIssues(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int fixAssertionIssues(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		changesMade += ensureTerms(task, loadedConcept);
		return changesMade;
	}

	private int ensureTerms(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		//TODO Need to add check that all components have been published before
		//inactivating.  If NOT published, should delete and recreate.
		//Loop through all the terms for the concept and for any active one:
		// 1. replace any double spaces with single spaces
		// 2. TBA
		//Then inactivate that term and replace with an otherwise identical one.
		List<Description> originalDescriptions = new ArrayList<Description>(concept.getDescriptions());
		for (Description d : originalDescriptions) {
			if (d.isActive()) {
				String newTerm = d.getTerm().replaceAll("    ", " ").replaceAll("   ", " ").replaceAll("  ", " ");
				if (!newTerm.equals(d.getTerm())) {
					Description replacement = d.clone(null);
					replacement.setTerm(newTerm);
					changesMade++;
					concept.addDescription(replacement);
					d.setActive(false);
					d.setEffectiveTime(null);
					d.setInactivationIndicator(InactivationIndicator.RETIRED);
					String msg = "Replaced term '" + d.getTerm() + "' with '" + replacement.getTerm() + "'.";
					report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
		}
		return changesMade;
	}

	@Override
	protected Batch formIntoBatch(List<Component> conceptsInFile) throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		List<Component> allConceptsBeingProcessed = new ArrayList<Component>();
		//Sort the concepts into groups per assigned Author
		for (Map.Entry<String, List<Concept>> thisEntry : groupByAuthor(conceptsInFile).entrySet()) {
			String[] author_reviewer = thisEntry.getKey().split("_");
			Task task = batch.addNewTask(author_reviewer);

			for (Concept thisConcept : thisEntry.getValue()) {
				if (task.size() >= taskSize) {
					task = batch.addNewTask(author_reviewer);
				}
				task.add(thisConcept);
				allConceptsBeingProcessed.add(thisConcept);
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_TO_PROCESS, allConceptsBeingProcessed);
		List <Component> reportedNotProcessed = validateAllInputConceptsBatched (conceptsInFile, allConceptsBeingProcessed);
		addSummaryInformation(REPORTED_NOT_PROCESSED, reportedNotProcessed);
		storeRemainder(CONCEPTS_IN_FILE, CONCEPTS_TO_PROCESS, REPORTED_NOT_PROCESSED, "Gone Missing");
		return batch;
	}

	/**Actually we're going to group by both Author and Reviewer **/
	private Map<String, List<Concept>> groupByAuthor(List<Component> conceptsInFile) {
		Map<String, List<Concept>> groupedByAuthor = new HashMap<String,List<Concept>>();
		for (Component thisComponent : conceptsInFile) {
			Concept thisConcept = (Concept)thisComponent;
			String author_reviewer = thisConcept.getAssignedAuthor() + "_" + thisConcept.getReviewer();
			List<Concept> thisAuthorsConcepts = groupedByAuthor.get(author_reviewer);
			if (thisAuthorsConcepts == null) {
				thisAuthorsConcepts = new ArrayList<Concept>();
				groupedByAuthor.put(author_reviewer, thisAuthorsConcepts);
			}
			thisAuthorsConcepts.add(thisConcept);
		}
		return groupedByAuthor;
	}

	private List<Component> validateAllInputConceptsBatched(List<Component> concepts,
			List<Component> allConceptsToBeProcessed) throws TermServerScriptException {
		List<Component> reportedNotProcessed = new ArrayList<Component>();
		//Ensure that all concepts we got given to process were captured in one batch or another
		for (Component thisConcept : concepts) {
			if (!allConceptsToBeProcessed.contains(thisConcept)) {
				reportedNotProcessed.add(thisConcept);
				String msg = thisConcept + " was given in input file but did not get included in a batch.";
				report(null, thisConcept, Severity.CRITICAL, ReportActionType.UNEXPECTED_CONDITION, msg);
			}
		}
		info("Processing " + allConceptsToBeProcessed.size() + " concepts.");
		return reportedNotProcessed;
	}

	@Override
	public String getScriptName() {
		return "AssertionFailureFix";
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[2]);
		c.setAssignedAuthor(lineItems[0]);
		c.setReviewer(lineItems[1]);
		c.addAssertionFailure(lineItems[3]);
		return Collections.singletonList(c);
	}

}
