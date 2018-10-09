package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;

import us.monoid.json.JSONObject;

/*
 * QI-21 Follow-up.   
 * Where we've erroneously inactivated a relationship and created a new one 
 * where we should instead have just modified the group number, deleted the 
 * new relationship and reactivate/shift the original relationship
 */
public class ShiftRelationshipGroupId extends BatchFix implements RF2Constants{
	
	boolean unpublishedContentOnly = true;
	
	protected ShiftRelationshipGroupId(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ShiftRelationshipGroupId fix = new ShiftRelationshipGroupId(null);
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
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = shiftRelationshipGroup(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int shiftRelationshipGroup(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//For each active unpublished relationship, see if we have an inactive one 
		//in another group.  
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.isReleased()) {
				List<Relationship> inactiveMatches = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.INACTIVE);
				if (inactiveMatches.size() > 1) {
					warn (c + " has multiple inactive matching relationships");
					//TODO If we have a few of these, we could select the brand new one
				} else if (inactiveMatches.size() == 1) {
					Relationship inactiveMatch = inactiveMatches.get(0);
					//Delete the new relationship, and move/re-activate the inactive one
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_REACTIVATED, "Moved + Reactivated from " + inactiveMatch.getGroupId() + " to " + r.getGroupId() + ": " + r);
					if (inactiveMatch.getEffectiveTime() != null  && !inactiveMatch.getEffectiveTime().isEmpty()) {
						report (t, c, Severity.HIGH, ReportActionType.INFO, "Relationship was historically inactive: " + inactiveMatch);
					}
					c.removeRelationship(r);
					inactiveMatch.setActive(true);
					inactiveMatch.setGroupId(r.getGroupId());
					changesMade += 3;
				}
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		print ("Identifying cloned relationships that should have been moved");
		this.setQuiet(true);
		for (Concept c : ROOT_CONCEPT.getDescendents(NOT_SET)) {
			//For each active unpublished relationship, see if we have an inactive one 
			//in another group
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.isReleased() == false && c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.INACTIVE).size() > 0) {
					processMe.add(c);
					break;
				}
			}
		}
		debug("Identified " + processMe.size() + " concepts to process");
		this.setQuiet(false);
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
