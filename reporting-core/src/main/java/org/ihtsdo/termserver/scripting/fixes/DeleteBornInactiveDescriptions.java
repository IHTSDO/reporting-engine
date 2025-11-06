package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * MSSP-1532 NL made the decision to remove born inactive descriptions
*/
public class DeleteBornInactiveDescriptions extends BatchFix implements ScriptConstants{
	
	protected DeleteBornInactiveDescriptions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteBornInactiveDescriptions fix = new DeleteBornInactiveDescriptions(null);
		try {
			fix.reportNoChange = false;  //Might just be langrefset which we'll modify directly
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			fix.additionalReportColumns = "Active, Details";
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, task.getBranchPath());
		int changesMade = deleteBornInactiveDescription(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, "");
			} catch (Exception e) {
				report(task, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int deleteBornInactiveDescription(Task t, Concept c) throws TermServerScriptException {
		Set<Description> descriptionsToRemove = new HashSet<>();
		for (Description d : c.getDescriptions()) {
			if (inScope(d) && !d.isActive() && !d.isReleased()) {
				descriptionsToRemove.add(d);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_DELETED,d);
			}
		}
		c.getDescriptions().removeAll(descriptionsToRemove);
		return descriptionsToRemove.size();
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				if (inScope(d) && !d.isActive() && !d.isReleased()) {
					allAffected.add(c);
					continue nextConcept;
				}
			}
		}
		return new ArrayList<Component>(allAffected);
	}
	
}
