package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
Assertion Failure fix checks a number of known assertion issues and makes
changes to the concepts if required
 */
public class FractureOfXTermRemodelling extends BatchFix implements ScriptConstants{
	
	String subHierarchyStr = "88230002";  // |Disorder of skeletal system (disorder)|
	String searchTerm = "fracture";
	String desiredTerm = "fracture of";
	String[] prefixes = new String[] {"open", "closed"};
	
	protected FractureOfXTermRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		FractureOfXTermRemodelling fix = new FractureOfXTermRemodelling(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't incude the project export in our timings
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		/*Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = inactivateLowerCaseTerm(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;*/
		report(task, concept, Severity.NONE, ReportActionType.INFO, "");
		return 1;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept>allDescendants = subHierarchy.getDescendants(NOT_SET);
		for (Concept thisConcept : allDescendants) {
			String fsn = thisConcept.getFsn().toLowerCase();
			if (fsn.contains(searchTerm) && !startsWithSearchDesiredTerm(fsn)) {
				processMe.add(thisConcept);
			}
		}
		return processMe;
	}

	private boolean startsWithSearchDesiredTerm(String fsn) {
		boolean startsWithSearchTerm = false;
		
		if (fsn.startsWith(desiredTerm)) {
			startsWithSearchTerm = true;
		} else {
			for (String prefix : prefixes) {
				if (fsn.startsWith(prefix + " " + desiredTerm)) {
					startsWithSearchTerm = true;
				}
			}
		}
		return startsWithSearchTerm;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
	
}
