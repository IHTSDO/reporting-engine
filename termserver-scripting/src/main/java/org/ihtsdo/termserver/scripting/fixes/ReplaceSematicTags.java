package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
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
Find matching semantic tags and swap for specified replacement
Currently verifies that the existing FSN is new and unpublished
-- Also adding in a cheeky fix while we're going through concepts to make GB only preferred terms acceptable in US dialect.
 */
public class ReplaceSematicTags extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static Map<String, String> replacementMap = new HashMap<String, String>();
	static {
		replacementMap.put("(virtual medicinal product form)","(medicinal product form)");
		replacementMap.put("(virtual medicinal product)","(medicinal product)");
		replacementMap.put("(virtual clinical drug)","(clinical drug)");
	}
	protected ReplaceSematicTags(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceSematicTags fix = new ReplaceSematicTags(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
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
		int changesMade = replaceSemanticTag(task, loadedConcept);
		if (changesMade > 0) {
			checkAllDescriptionsAcceptable(task, loadedConcept);
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

	private void checkAllDescriptionsAcceptable(Task task, Concept loadedConcept) throws TermServerScriptException {
		//If we have two preferred terms, then make the GB Pref Term also acceptable in US Dialect
		List<Description> synonyms = loadedConcept.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		for (Description d : synonyms) {
			Map<String, Acceptability> acceptabilityMap = d.getAcceptabilityMap();
			if (acceptabilityMap.size() == 1) {
				//If we only have one acceptability for this term, add the other one.
				String missing = acceptabilityMap.containsKey(GB_ENG_LANG_REFSET)?US_ENG_LANG_REFSET:GB_ENG_LANG_REFSET;
				acceptabilityMap.put(missing, Acceptability.ACCEPTABLE);
				String msg = "Added " + (missing.equals(GB_ENG_LANG_REFSET) ? "GB":"US") + " acceptability";
				report(task, loadedConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
			}
		}
	}

	private int replaceSemanticTag(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> fsnList = concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE);
		if (fsnList.size() != 1) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Number of active FSNs encountered was: " + fsnList.size());
		} else {
			Description fsn = fsnList.get(0);
			String[] fsnParts = SnomedUtils.deconstructFSN(fsn.getTerm());
			String newFSN = null;
			for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
				if (fsnParts[1].equals(entry.getKey())) {
					newFSN = fsnParts[0] + " " + entry.getValue();
				}
			}
			if (newFSN != null) {
				if (termAlreadyExists(concept, newFSN)) {
					throw new TermServerScriptException("Term already exists: " + newFSN);
				}
				if (fsn.getEffectiveTime() != null) {
					throw new TermServerScriptException("Detected attempt to delete published description");
				}
				//This will cause the existing fsn to be deleted and a new one created.
				fsn.setDescriptionId(null);
				fsn.setTerm(newFSN);
				changesMade++;
				report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "FSN set to " + newFSN);
			}
		}
		return changesMade;
	}

	private boolean termAlreadyExists(Concept concept, String newTerm) {
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions()) {
			if (description.getTerm().equals(newTerm)) {
				termAlreadyExists = true;
			}
		}
		return termAlreadyExists;
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
		Set<Concept> allPotential = GraphLoader.getGraphLoader().getConcept(subHierarchyStr).getDescendents(NOT_SET);
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept thisConcept : allPotential) {
			for (String replaceTag : replacementMap.keySet()) {
				if (thisConcept.getFSNDescription().getTerm().contains(replaceTag)) {
					allAffected.add(thisConcept);
				}
			}
		}
		return allAffected;
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

	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerScriptException {
		throw new NotImplementedException();
	}
}
