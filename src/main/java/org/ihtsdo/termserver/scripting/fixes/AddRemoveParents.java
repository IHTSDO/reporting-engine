package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
INFRA-2302 Driven by a text file of concepts add or remove parent relationships as indicated
*/
public class AddRemoveParents extends BatchFix implements RF2Constants{
	
	Map<Concept, RelationshipGroup> changeMap = new HashMap<>();
	
	protected AddRemoveParents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		AddRemoveParents fix = new AddRemoveParents(null);
		try {
			fix.inputFileHasHeaderRow = true;
			fix.runStandAlone = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.startTimer();
			fix.processFile();
			info ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = addRemoveParents(task, loadedConcept);
		
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int addRemoveParents(Task t, Concept c) throws TermServerScriptException {
		
		int changesMade = 0;
		List<Relationship> parentRels = new ArrayList<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE));
		//Work through the relationship changes we have for this concept and decide if we're adding or inactivating
		for (Relationship r : changeMap.get(c).getRelationships()) {
			if (r.isActive()) {
				//changesMade++;
				//c.addRelationship(r);
				//report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, r);
			} else {
				//Find this relationship to inactivate / delete
				for (Relationship p : parentRels) {
					if (p.getTarget().equals(r.getTarget())) {
						changesMade += removeParentRelationship(t, p, c, null, null);
					}
				}
			}
		}
		return changesMade;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		RelationshipGroup g = changeMap.get(c);
		if (g == null) {
			g = new RelationshipGroup(UNGROUPED);
			changeMap.put(c,  g);
		}
		Concept target = gl.getConcept(lineItems[1]);
		Relationship r = new Relationship (c, IS_A, target, UNGROUPED);
		r.setActive(lineItems[2].equals(ACTIVE_FLAG));
		g.addRelationship(r);
		return c;
	}
}
