package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
Fix finds terms where the 2nd word is lower case and no equivalent upper case term exists.
The lower case term is inactivated and replaced with the upper case version.
 */
public class ReplaceLowerCaseTerms extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	String subHierarchyStr = "27268008";  //Genus Salmonella (organism)
	String[] exceptions = new String[] {"398393000", "110378009"};
	String firstWord = "Salmonella";
	
	protected ReplaceLowerCaseTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceLowerCaseTerms fix = new ReplaceLowerCaseTerms(null);
		try {
			fix.useAuthenticatedCookie = true;
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
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
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = replaceLowerCaseTerm(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int replaceLowerCaseTerm(Task task, Concept concept) {
		int changesMade = 0;
		Description lower = findUnmatchedLowerCaseTerm(concept);
		
		if (lower != null) {
			changesMade++;
			String[] words = lower.getTerm().split(" ");
			words[1] = SnomedUtils.capitalize(words[1]);
			Description upper = lower.clone("");
			upper.setTerm(StringUtils.join(words, " "));
			concept.addDescription(upper);
			lower.setActive(false);
			lower.setEffectiveTime(null);
			lower.setInactivationIndicator(InactivationIndicator.ERRONEOUS);
			String msg = "Replaced term '" + lower.getTerm() + "' with '" + upper.getTerm() + "'.";
			report(task, concept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
		}
		return changesMade;
	}

	protected Batch formIntoBatch() throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask();
		List<Concept> allConceptsBeingProcessed = identifyConceptsToProcess();

		for (Concept thisConcept : allConceptsBeingProcessed) {
			if (task.size() >= taskSize) {
				task = batch.addNewTask();
				setAuthorReviewer(task, author_reviewer);
			}
			task.add(thisConcept);
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConceptsBeingProcessed);
		//storeRemainder(CONCEPTS_IN_FILE, CONCEPTS_PROCESSED, REPORTED_NOT_PROCESSED, "Gone Missing");
		return batch;
	}
	

	private List<Concept> identifyConceptsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<Concept>();
		GraphLoader gl = GraphLoader.getGraphLoader();
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept>allDescendants = subHierarchy.getDescendents(NOT_SET);
		for (Concept thisConcept : allDescendants) {
			//Don't process the exceptions
			if (ArrayUtils.contains(exceptions, thisConcept.getConceptId())) {
				continue;
			}
			//Find concepts where there are otherwise identical lower and uppercase terms
			Description unmatchedLowerCaseTerm = findUnmatchedLowerCaseTerm(thisConcept);
			if (unmatchedLowerCaseTerm != null) {
				processMe.add(thisConcept);
			}
		}
		return processMe;
	}

	//Find active descriptions that consist of two words, where the second word is 
	//lower case, and there is no other active term where it is upper case.
	private Description findUnmatchedLowerCaseTerm(Concept thisConcept) {
		for (Description lowerCase : thisConcept.getDescriptions(ActiveState.ACTIVE)) {
			if (lowerCase.getType().equals(DescriptionType.SYNONYM)) {  //Only comparing Synonyms 
				//Do we only have two words?  And is word 2 all lower case?
				//No interested in words containing numbers
				String [] words = lowerCase.getTerm().split(" ");
				if (words.length == 2 && 
						words[0].equals(firstWord) && 
						!words[1].matches(".*\\d+.*") &&
						words[1].equals(words[1].toLowerCase())) {
					//Are there no other terms differing only in case?
					if (!hasCaseDifferenceTerm(thisConcept, lowerCase)) {
						return lowerCase;
					}
				}
			}
		}
		return null;
	}

	/*
	 * Returns true if an active description exists which differs only in case.
	 */
	private boolean hasCaseDifferenceTerm(Concept concept,
			Description matchTerm) {
		for (Description thisTerm : concept.getDescriptions(ActiveState.ACTIVE)) {
			//Check it's not exactly the same
			if (!matchTerm.getTerm().equals(thisTerm.getTerm())) {
				//Check if it matches ignoring case
				if (matchTerm.getTerm().equalsIgnoreCase(thisTerm.getTerm())) {
					return true;
				}
			}
		}
		return false;
	}

	private void setAuthorReviewer(Task task, String[] author_reviewer) {
		task.setAssignedAuthor(author_reviewer[0]);
		if (author_reviewer.length > 1) {
			task.setReviewer(author_reviewer[1]);
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
	
	class MatchedSet {
		MatchedSet (Description keep, Description inactivate) {
			this.keep = keep;
			this.inactivate = inactivate;
		}
		Description keep;
		Description inactivate;
	}

	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerScriptException {
		throw new NotImplementedException();
	}

}
