package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/*
	Where a concept is inactive, it's active descriptions should have the inactivation indicator 
	900000000000495008 |Concept non-current (foundation metadata concept)| applied against them.
	See INFRA-1407
	Also ISRS-225
*/
public class FixMissingInactivationIndicators extends BatchFix implements RF2Constants{
	
	Map<String, ConceptChange> conceptsToProcess = new HashMap<String, ConceptChange>();
	Set<Integer> reportedItems = new HashSet<Integer>();
	
	protected FixMissingInactivationIndicators(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		FixMissingInactivationIndicators fix = new FixMissingInactivationIndicators(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			if (dryRun) {
				fix.runStandAlone = true;
			}
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.startTimer();
			println ("Processing started.  See results: " + fix.reportFile.getAbsolutePath());
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (tsConcept.isActive()) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active. Won't be adding inactivation indicators to descriptions.");
		} else {
			changesMade = addConceptNonCurrentIndicator(task, tsConcept, false);
			if (changesMade > 0) {
				try {
					String conceptSerialised = gson.toJson(tsConcept);
					debug ((dryRun?"Dry run updating":"Updating") + " state of " + tsConcept + info);
					if (!dryRun) {
						tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
					}
					//report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept successfully remodelled. " + changesMade + " changes made.");
				} catch (Exception e) {
					//See if we can get that 2nd level exception's reason which says what the problem actually was
					String additionalInfo = "";
					if (e.getCause().getCause() != null) {
						additionalInfo = " - " + e.getCause().getCause().getMessage().replaceAll(COMMA, " ").replaceAll(QUOTE, "'");
					} 
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getClass().getSimpleName()  + " - " + additionalInfo);
					e.printStackTrace();
				}
			}
		}
		return changesMade;
	}

	private int addConceptNonCurrentIndicator(Task task, Concept c, boolean trialRun) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getInactivationIndicator() == null) {
				d.setInactivationIndicator(InactivationIndicator.CONCEPT_NON_CURRENT);
				report(task, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "CNC Inactivation indicator added to " + d);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		println ("Identifying concepts to process");
		List<Component> processMe = new ArrayList<Component>();
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getInactivationIndicator() == null) {
						processMe.add(c);
						continue nextConcept;
					}
				}
			}
		}
		println ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}

}
