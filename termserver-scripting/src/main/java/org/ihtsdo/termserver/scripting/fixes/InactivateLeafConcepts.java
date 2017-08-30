package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import us.monoid.json.JSONObject;

/*
 * DRUGS-321 
 * Inactivate concepts (functionality currently broken in production) 
 * and check that the concept is a leaf node, otherwise this is obviously not safe.
 */
public class InactivateLeafConcepts extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	List<Concept> conceptsToInactivate = new ArrayList<Concept>();
	
	protected InactivateLeafConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		InactivateLeafConcepts fix = new InactivateLeafConcepts(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); //Just the FSNs
			//We won't incude the project export in our timings
			fix.loadDescIds();
			fix.startTimer();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	
	private void loadDescIds() throws IOException, TermServerScriptException {
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		println ("Loading concepts to inactivate from " + inputFile);
		for (String line : lines) {
			//Skip any empty lines
			if (line.trim().isEmpty()) {
				continue;
			}
			//format:   SCTID  FSN
			String[] columns = line.split(TAB);
			Concept c = gl.getConcept(columns[0]);
			if (!c.getFsn().equals(columns[1])) {
				report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "FSN failed to match expected value " + columns[1]);
			} else if (!c.isActive()) {
				report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is already inactive");
			} else if ( gl.usedAsHistoricalAssociationTarget(c) != null) {
				report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is used as the target of a historical association");
			} else if ( c.getDescendents(IMMEDIATE_CHILD).size() > 0) {
				report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is not a leaf node.  Not safe to inactivate");
			} else {
				conceptsToInactivate.add(c);
			}
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = inactivateConcept(task, loadedConcept);
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

	private int inactivateConcept(Task task, Concept concept) throws TermServerScriptException {
		concept.setActive(false);
		concept.setEffectiveTime(null);
		concept.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated");
		return 1;
	}

	protected Batch formIntoBatch() throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask();

		for (Concept thisConcept : conceptsToInactivate) {
			if (task.size() >= taskSize) {
				task = batch.addNewTask();
				setAuthorReviewer(task, author_reviewer);
			}
			task.add(thisConcept);
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, conceptsToInactivate);
		return batch;
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
