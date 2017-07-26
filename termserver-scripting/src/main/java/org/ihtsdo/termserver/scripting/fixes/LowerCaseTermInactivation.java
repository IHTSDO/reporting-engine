package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
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
Fix identifies otherwise identical lower case and upper case terms and inactivates
the lower case term
 */
public class LowerCaseTermInactivation extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	String subHierarchyStr = "27268008";  //Genus Salmonella (organism)
	
	protected LowerCaseTermInactivation(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		LowerCaseTermInactivation fix = new LowerCaseTermInactivation(null);
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
		int changesMade = inactivateLowerCaseTerm(task, loadedConcept);
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

	private int inactivateLowerCaseTerm(Task task, Concept concept) {
		int changesMade = 0;
		MatchedSet m = findMatchingDescriptionSet(concept);
		
		if (m != null) {
			changesMade++;
			m.inactivate.setActive(false);
			m.inactivate.setEffectiveTime(null);
			m.inactivate.setInactivationIndicator(InactivationIndicator.ERRONEOUS);
			String msg = "Inactivated term '" + m.inactivate.getTerm() + "' due to presence of '" + m.keep.getTerm() + "'.";
			report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
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
			//Find concepts where there are otherwise identical lower and uppercase terms
			MatchedSet lowerCaseMatchingSet = findMatchingDescriptionSet(thisConcept);
			if (lowerCaseMatchingSet != null) {
				processMe.add(thisConcept);
			}
		}
		return processMe;
	}

	//Find active descriptions that match, where we want to keep the one that has the second
	//word capitalized, and inactivate the one that has the second word in lower case.
	private MatchedSet findMatchingDescriptionSet(Concept thisConcept) {
		for (Description upperCase : thisConcept.getDescriptions(ActiveState.ACTIVE)) {
			if (upperCase.getType().equals(DescriptionType.SYNONYM)) {  //Only comparing Synonyms 
				for (Description lowerCase : thisConcept.getDescriptions(ActiveState.ACTIVE)) {	
					if (lowerCase.getType().equals(DescriptionType.SYNONYM) && 
							upperCase != lowerCase && 
							upperCase.getTerm().equalsIgnoreCase(lowerCase.getTerm())) {
						if (lowerCase.getTerm().equals(SnomedUtils.initialCapitalOnly(lowerCase.getTerm()))) {
							return new MatchedSet (upperCase, lowerCase);
						}
					}
				}
			}
		}
		return null;
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
