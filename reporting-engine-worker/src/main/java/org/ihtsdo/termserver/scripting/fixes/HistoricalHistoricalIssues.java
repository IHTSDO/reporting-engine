package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
INFRA-2454 Find issues with historical inactivation reasons and associations and
fix them
*/
public class HistoricalHistoricalIssues extends BatchFix implements RF2Constants{
	
	protected HistoricalHistoricalIssues(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		HistoricalHistoricalIssues fix = new HistoricalHistoricalIssues(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = fixHistoricalIssues(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, "");
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int fixHistoricalIssues(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//First make sure we only have one inactivation indicator 
		for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
			
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return Collections.singletonList(new Concept(lineItems[0]));
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Collection<Concept> allPotential = gl.getAllConcepts();
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : allPotential) {
			if (!c.isActive()) {
				//Firstly, do we have multiple inactivation indicators?
				if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					allAffected.add(c);
				} else {
					String refsetId = "";
					for ( AssociationEntry h : c.getAssociations(ActiveState.ACTIVE)) {
						if (refsetId.isEmpty()) {
							refsetId = h.getRefsetId();
						} else {
							//Is this association type different from the first one we saw?
							if (!h.getRefsetId().equals(refsetId)) {
								allAffected.add(c);
								break;
							}
						}
					}
				}
			}
		}
		return new ArrayList<Component>(allAffected);
	}
	
}
