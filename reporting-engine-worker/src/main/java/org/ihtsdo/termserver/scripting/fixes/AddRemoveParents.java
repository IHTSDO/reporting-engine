package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
* INFRA-2302, INFRA-2344, INFRA-2456, INFRA-2529, INFRA-2571
* Driven by a text file of concepts add or remove parent relationships as indicated
*/
public class AddRemoveParents extends BatchFix implements RF2Constants{
	
	Map<Concept, RelationshipGroup> changeMap = new HashMap<>();
	
	protected AddRemoveParents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		AddRemoveParents fix = new AddRemoveParents(null);
		try {
			fix.inputFileHasHeaderRow = true;
			fix.runStandAlone = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;  //With Bones, we're hitting the limit of 32K
			fix.additionalReportColumns = "Action Detail";
			fix.expectNullConcepts = true;
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = addRemoveParents(task, loadedConcept);
			String conceptSerialised = gson.toJson(loadedConcept);
			if (changesMade > 0) {
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int addRemoveParents(Task t, Concept c) throws TermServerScriptException {
		
		int changesMade = 0;
		List<Relationship> parentRels = new ArrayList<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE));
		//Work through the relationship changes we have for this concept and decide if we're adding or inactivating
		for (Relationship r : changeMap.get(c).getRelationships()) {
			if (r.isActive()) {
				if (c.getRelationships(r, ActiveState.ACTIVE).size() > 0 ) {
					report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Relationship already present: " + r);
				} else if (c.getRelationships(r, ActiveState.INACTIVE).size() > 0 ) {
					Relationship inactive = c.getRelationships(r, ActiveState.INACTIVE).get(0);
					report (t, c, Severity.MEDIUM, ReportActionType.INFO, "Relationship already present, but inactive: " + inactive);
					inactive.setActive(true);
					report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REACTIVATED, inactive);
					changesMade++;
				} else {
					changesMade++;
					c.addRelationship(r);
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, r);
				}
			} else {
				//Find this relationship to inactivate / delete
				for (Relationship p : parentRels) {
					if (p.getTarget().equals(r.getTarget())) {
						changesMade += removeParentRelationship(t, p, c, null, null);
					}
				}
			}
		}
		
		//Check we haven't ended up an orphan
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE).size() == 0) {
			throw new ValidationFailure(c, "Attempt to create orphan rejected");
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (!c.isActive()) {
			report ((Task)null, c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Cannot modify concept - is inactive");
			return null;
		}
		if (lineItems[2].equals(ACTIVE_FLAG)) {
		//if (!lineItems[2].equals(ACTIVE_FLAG)) {
			RelationshipGroup g = changeMap.get(c);
			if (g == null) {
				g = new RelationshipGroup(UNGROUPED);
				changeMap.put(c,  g);
			}
			Concept target = gl.getConcept(lineItems[1]);
			if (!target.isActive()) {
				report ((Task)null, c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Cannot modify parent, concept is inactive: " + target);
				return null;
			}
			Relationship r = new Relationship (c, IS_A, target, UNGROUPED);
			r.setActive(lineItems[2].equals(ACTIVE_FLAG));
			g.addRelationship(r);
			return Collections.singletonList(c);
		} else {
			return null;
		}
	}
}
