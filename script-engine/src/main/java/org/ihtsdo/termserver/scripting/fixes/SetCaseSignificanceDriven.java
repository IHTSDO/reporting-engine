package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.*;

public class SetCaseSignificanceDriven extends BatchFix implements ScriptConstants{


	protected SetCaseSignificanceDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SetCaseSignificanceDriven fix = new SetCaseSignificanceDriven(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.inputFileHasHeaderRow = false;
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = setCaseSignificance(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		String descriptionStr = SnomedUtils.getDescriptionsFull(loadedConcept);
		report(t, loadedConcept, Severity.NONE, ReportActionType.INFO, descriptionStr);
		return changesMade;
	}

	private int setCaseSignificance(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//Any descriptions that start with the same first word, should have their
		//case significance set to EN
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getCaseSignificance() != CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) {
				String origCS = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, origCS + " -> CS", d);
				changesMade ++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		//A bit unusual - we're being driven by a file, but we've only got the FSN, so look that up.
		Concept c = gl.findConcept(lineItems[0]);
		if (c == null) {
			throw new TermServerScriptException("Couldn't find concept for " + lineItems[0]);
		}
		return Collections.singletonList(c);
	}

}
