package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;

import us.monoid.json.JSONObject;

/*
For APDS-335, QI
Replace all relationships of one specified type / value for another
*/
public class ReplaceRelationship extends BatchFix implements RF2Constants{
	
	Concept findAttribute;
	Concept replaceAttribute;
	
	enum Mode { TYPE, VALUE }
	Mode mode = Mode.VALUE;
	
	protected ReplaceRelationship(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceRelationship fix = new ReplaceRelationship(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = false;
			fix.populateTaskDescription = false;  //Going above some limit
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		
		//Populate our attributes of interest
		findAttribute = gl.getConcept("23583003 |Inflammation (morphologic abnormality)|");  
		replaceAttribute = gl.getConcept("409774005 |Inflammatory morphology (morphologic abnormality)|"); 
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = replaceTargetRelationship(task, loadedConcept);
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

	private int replaceTargetRelationship(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			Concept match = mode.equals(Mode.TYPE) ? r.getType() : r.getTarget();
			if (match.equals(findAttribute)) {
				//Clone r and modify
				Relationship replacement = r.clone(null);
				switch (mode) {
				case TYPE : replacement.setType(replaceAttribute);
						break;
				case VALUE : 
					replacement.setTarget(replaceAttribute);
				}
				
				replaceRelationship(t, c, r, replacement);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Collection<Concept> allPotential = gl.getAllConcepts();
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : allPotential) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				Concept match = mode.equals(Mode.TYPE) ? r.getType() : r.getTarget();
				if (match.equals(findAttribute)) {
					allAffected.add(c);
					break;
				}
			}
		}
		info ("Detected " + allAffected.size() + " concepts to modify");
		return new ArrayList<Component>(allAffected);
	}

}
