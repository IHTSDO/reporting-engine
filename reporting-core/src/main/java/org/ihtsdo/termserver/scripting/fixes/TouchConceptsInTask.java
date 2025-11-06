package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * Flip the case of the FSN and back in order to force a concept to show up in review
 */
public class TouchConceptsInTask extends BatchFix implements ScriptConstants{
	
	final static String targetTaskPath = "MAIN/CRSJUL20/CRSJUL20-613";
	
	protected TouchConceptsInTask(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		TouchConceptsInTask fix = new TouchConceptsInTask(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"); //Ad-Hoc Batch Updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.expectNullConcepts = true; //We'll only return an sctid the first time we see it.
			fix.dryRun = false;  //We're doing this for content that doesn't exist in the snapshot, only the task
			fix.validateConceptOnUpdate = false;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.touchConcepts();
		} finally {
			fix.finish();
		}
	}

	public int touchConcepts() throws TermServerScriptException {
		Batch b = new Batch ("foo");
		Task t = b.addNewTask("bar", null);
		t.setBranchPath(targetTaskPath);
		allComponentsToProcess = processFile(getInputFile());
		for (Concept c : asConcepts(allComponentsToProcess)) {
			Concept loadedConcept = loadConcept(c, targetTaskPath);
			Description fsn = loadedConcept.getFSNDescription();
			if (fsn == null) {
				//Concept not found?
				report(t, c , Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept was specified to touch, but not found in task", targetTaskPath);
				continue;
			}
			CaseSignificance orig = fsn.getCaseSignificance();
			CaseSignificance flip = orig.equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) ? CaseSignificance.CASE_INSENSITIVE : CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			fsn.setCaseSignificance(flip);
			try {
				updateConcept(t, loadedConcept, "");
				fsn.setCaseSignificance(orig);
				updateConcept(t, loadedConcept, "");
				report(t, c , Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, "Flipped case sign on FSN");
			} catch (Exception e) {
				report(t, c , Severity.CRITICAL, ReportActionType.API_ERROR, "Some issue saving concept", org.ihtsdo.otf.utils.ExceptionUtils.getExceptionCause(e.getMessage(), e));
			}
		}
		return NO_CHANGES_MADE;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return  Collections.singletonList(new Concept(lineItems[0]));
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		throw new NotImplementedException();
	}
	
}
