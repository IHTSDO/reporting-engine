package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * INFRA-3759 Modify CS settings per supplied spreadsheet
 */
public class CaseSignificanceFixDriven extends BatchFix implements RF2Constants{
	
	Map<Concept, Map<String, String>> csMap = new HashMap<>();
	
	protected CaseSignificanceFixDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		CaseSignificanceFixDriven fix = new CaseSignificanceFixDriven(null);
		try {
			ReportSheetManager.targetFolderId="1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			fix.additionalReportColumns = "previously, new setting";
			fix.inputFileHasHeaderRow = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
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
		//What changes are we making to this concept
		Map<String, String> descChanges = csMap.get(c);
		for (Description d : c.getDescriptions()) {
			if (descChanges.containsKey(d.getId())) {
				String before = d.toString();
				CaseSignificance newSetting = SnomedUtils.translateCaseSignificanceToEnum(descChanges.get(d.getId()));
				if (d.getCaseSignificance().equals(newSetting)) {
					report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "CaseSignificance was already set to " + newSetting);
				} else {
					d.setCaseSignificance(newSetting);
					report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, before, d);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	@Override
	//File format id	termid	replacement
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		Map<String, String> descChanges = csMap.get(c);
		if (descChanges == null) {
			descChanges = new HashMap<>();
			csMap.put(c, descChanges);
		}
		descChanges.put(lineItems[1], lineItems[2]);
		return Collections.singletonList(c);
	}
}
