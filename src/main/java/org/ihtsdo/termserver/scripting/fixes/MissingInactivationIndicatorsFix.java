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
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/*
	Where a concept is inactive, it's active descriptions should have the inactivation indicator 
	900000000000495008 |Concept non-current (foundation metadata concept)| applied against them.
	See INFRA-1407 and ISRS-225
	
	This class deprecated because SnowOwl is currently re-using UUIDs for this, which we'd have to 
	do another fix to address.   Use the same class in the delta package.
*/
@Deprecated
public class MissingInactivationIndicatorsFix extends BatchFix implements RF2Constants{
	
	Map<String, ConceptChange> conceptsToProcess = new HashMap<String, ConceptChange>();
	Set<Integer> reportedItems = new HashSet<Integer>();
	
	protected MissingInactivationIndicatorsFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		MissingInactivationIndicatorsFix fix = new MissingInactivationIndicatorsFix(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
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
				report(task, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "CNC Inactivation indicator added", d);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		info ("Identifying concepts to process");
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
		info ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}

}
