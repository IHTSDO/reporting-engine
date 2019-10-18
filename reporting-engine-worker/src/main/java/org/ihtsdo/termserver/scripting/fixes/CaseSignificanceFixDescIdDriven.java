package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/*
 * INFRA-3926 Modify case significance from CS to cI as per list
 */
public class CaseSignificanceFixDescIdDriven extends BatchFix implements RF2Constants{
	
	Map<Concept, List<Description>> changeSet = new HashMap<>();
	
	protected CaseSignificanceFixDescIdDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CaseSignificanceFixDescIdDriven fix = new CaseSignificanceFixDescIdDriven(null);
		try {
			ReportSheetManager.targetFolderId="1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			fix.additionalReportColumns = "previously, new setting";
			fix.inputFileHasHeaderRow = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.expectNullConcepts = true; //We'll only return an sctid the first time we see it.
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = modifyCS(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int modifyCS(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description localD : changeSet.get(c)) {
			Description d = c.getDescription(localD.getDescriptionId());
			if (d == null) {
				//Note that, when running locally, the clone does not include inactive components so we'll see this.
				report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Description id not available in TS", localD.getId());
			} else if (!d.isActive()) {
				report (t, c, Severity.HIGH, ReportActionType.NO_CHANGE, "Description is inactive", d);
			} else if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
				report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "CaseSignificance unexpected: " + d.getCaseSignificance());
			} else {
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
				report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE,  d);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	//File format descId
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		
		Description d = gl.getDescription(lineItems[0]);
		Concept c = gl.getConcept(d.getConceptId());
		//Have we seen this descriptions concept before?
		boolean firstTimeView = false;
		List<Description> descList = changeSet.get(c);
		if (descList == null) {
			descList = new ArrayList<Description>();
			changeSet.put(c, descList);
			firstTimeView = true;
		}
		descList.add(d);
		return firstTimeView ? Collections.singletonList(c) : null;
	}
}
