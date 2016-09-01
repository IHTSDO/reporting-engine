package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
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

	public static void main(String[] args) throws TermServerFixException, IOException, SnowOwlClientException {
		AssertionFailureFix fix = new AssertionFailureFix(null);
		try {
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot();
			//We won't incude the project export in our timings
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerFixException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept) throws TermServerFixException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = fixAssertionIssues(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int fixAssertionIssues(Task task, Concept loadedConcept) {
		int changesMade = 0;
		changesMade += ensureTerms(task, loadedConcept);
		return changesMade;
	}

	private int ensureTerms(Task task, Concept concept) {
		int changesMade = 0;
		//Loop through all the terms for the concept and for any active one:
		// 1. replace any double spaces with single spaces
		// 2. TBA
		//Then inactivate that term and replace with an otherwise identical one.
		List<Description> originalDescriptions = new ArrayList<Description>(concept.getDescriptions());
		for (Description d : originalDescriptions) {
			if (d.isActive()) {
				String newTerm = d.getTerm().replaceAll("    ", " ").replaceAll("   ", " ").replaceAll("  ", " ");
				if (!newTerm.equals(d.getTerm())) {
					Description replacement = d.clone();
					replacement.setTerm(newTerm);
					changesMade++;
					concept.addDescription(replacement);
					d.setActive(false);
					d.setEffectiveTime(null);
					d.setInactivationIndicator(InactivationIndicator.RETIRED);
					String msg = "Replaced term '" + d.getTerm() + "' with '" + replacement.getTerm() + "'.";
					report(task, concept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
		}
		return changesMade;
	}

	@Override
	Batch formIntoBatch(String fileName, List<Concept> conceptsInFile, String branchPath) throws TermServerFixException {
		Batch batch = new Batch(fileName);
		List<Concept> allConceptsBeingProcessed = new ArrayList<Concept>();
		//Sort the concepts into groups per assigned Author
		for (Map.Entry<String, List<Concept>> thisEntry : groupByAuthor(conceptsInFile).entrySet()) {
			String[] author_reviewer = thisEntry.getKey().split("_");
			Task task = batch.addNewTask();
			setAuthorReviewer(task, author_reviewer);

			for (Concept thisConcept : thisEntry.getValue()) {
				if (task.size() >= taskSize) {
					task = batch.addNewTask();
					setAuthorReviewer(task, author_reviewer);
				}
				task.add(thisConcept);
				allConceptsBeingProcessed.add(thisConcept);
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConceptsBeingProcessed);
		List <Concept> reportedNotProcessed = validateAllInputConceptsBatched (conceptsInFile, allConceptsBeingProcessed);
		addSummaryInformation(REPORTED_NOT_PROCESSED, reportedNotProcessed);
		storeRemainder(CONCEPTS_IN_FILE, CONCEPTS_PROCESSED, REPORTED_NOT_PROCESSED, "Gone Missing");
		return batch;
	}
	

	private void setAuthorReviewer(Task task, String[] author_reviewer) {
		task.setAssignedAuthor(author_reviewer[0]);
		if (author_reviewer.length > 1) {
			task.setReviewer(author_reviewer[1]);
		}
	}

	/**Actually we're going to group by both Author and Reviewer **/
	private Map<String, List<Concept>> groupByAuthor(List<Concept> conceptsInFile) {
		Map<String, List<Concept>> groupedByAuthor = new HashMap<String,List<Concept>>();
		for (Concept thisConcept : conceptsInFile) {
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

	private List<Concept> validateAllInputConceptsBatched(List<Concept> concepts,
			List<Concept> allConceptsToBeProcessed) {
		List<Concept> reportedNotProcessed = new ArrayList<Concept>();
		//Ensure that all concepts we got given to process were captured in one batch or another
		for (Concept thisConcept : concepts) {
			if (!allConceptsToBeProcessed.contains(thisConcept)) {
				reportedNotProcessed.add(thisConcept);
				String msg = thisConcept + " was given in input file but did not get included in a batch.";
				report(null, thisConcept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.UNEXPECTED_CONDITION, msg);
			}
		}
		println("Processing " + allConceptsToBeProcessed.size() + " concepts.");
		return reportedNotProcessed;
	}

	@Override
	public String getFixName() {
		return "AssertionFailureFix";
	}

	@Override
	Concept loadLine(String[] lineItems) throws TermServerFixException {
		Concept c = graph.getConcept(lineItems[2]);
		c.setAssignedAuthor(lineItems[0]);
		c.setReviewer(lineItems[1]);
		c.addAssertionFailure(lineItems[3]);
		return c;
	}

}
