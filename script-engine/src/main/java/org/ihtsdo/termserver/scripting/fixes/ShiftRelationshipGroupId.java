package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * QI-21 Follow-up.   
 * Where we've erroneously inactivated a relationship and created a new one 
 * where we should instead have just modified the group number, deleted the 
 * new relationship and reactivate/shift the original relationship
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShiftRelationshipGroupId extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ShiftRelationshipGroupId.class);

	protected ShiftRelationshipGroupId(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
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

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = shiftRelationshipGroup(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int shiftRelationshipGroup(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//For each active unpublished relationship, see if we have an inactive one 
		//in another group.  
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.isReleased()) {
				Set<Relationship> inactiveMatches = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.INACTIVE);
				if (inactiveMatches.size() > 1) {
					LOGGER.warn (c + " has multiple inactive matching relationships");
					//TODO If we have a few of these, we could select the brand new one
				} else if (inactiveMatches.size() == 1) {
					Relationship inactiveMatch = inactiveMatches.iterator().next();
					//Delete the new relationship, and move/re-activate the inactive one
					report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_REACTIVATED, "Moved + Reactivated from " + inactiveMatch.getGroupId() + " to " + r.getGroupId() + ": " + r);
					if (inactiveMatch.getEffectiveTime() != null  && !inactiveMatch.getEffectiveTime().isEmpty()) {
						report(t, c, Severity.HIGH, ReportActionType.INFO, "Relationship was historically inactive: " + inactiveMatch);
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
		for (Concept c : ROOT_CONCEPT.getDescendants(NOT_SET)) {
			//For each active unpublished relationship, see if we have an inactive one 
			//in another group
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.isReleased() == false && c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.INACTIVE).size() > 0) {
					processMe.add(c);
					break;
				}
			}
		}
		LOGGER.debug("Identified " + processMe.size() + " concepts to process");
		this.setQuiet(false);
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
