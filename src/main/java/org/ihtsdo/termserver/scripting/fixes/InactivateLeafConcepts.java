package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
 * DRUGS-321, DRUGS-479
 * Inactivate concepts (functionality currently broken in production) 
 * and check that the concept is a leaf node, otherwise this is obviously not safe.
 */
public class InactivateLeafConcepts extends BatchFix implements RF2Constants{
	
	protected InactivateLeafConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		InactivateLeafConcepts fix = new InactivateLeafConcepts(null);
		try {
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Just the FSNs
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = inactivateConcept(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun?"Dry run updating":"Updating") + " state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int inactivateConcept(Task task, Concept concept) throws TermServerScriptException {
		concept.setActive(false);
		concept.setEffectiveTime(null);
		concept.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated");
		return 1;
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (!c.getFsn().equals(lineItems[1])) {
			report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "FSN failed to match expected value " + lineItems[1]);
		} else if (!c.isActive()) {
			report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is already inactive");
		} else if ( gl.usedAsHistoricalAssociationTarget(c) != null) {
			String allTargets = gl.listAssociationParticipation(c);
			warn (allTargets);
			report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is used as the target of a historical association: " + allTargets);
		} else if ( c.getDescendents(IMMEDIATE_CHILD).size() > 0) {
			report(null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is not a leaf node.  Not safe to inactivate");
		} else {
			return Collections.singletonList(c);
		}
		return null;
	}
}
