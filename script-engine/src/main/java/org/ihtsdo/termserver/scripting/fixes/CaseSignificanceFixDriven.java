package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * INFRA-3759 Modify CS settings per supplied spreadsheet
 * INFRA-4166 Same deal, different format
 */
public class CaseSignificanceFixDriven extends BatchFix implements RF2Constants{
	
	Map<Concept, Map<String, String>> csMap = new HashMap<>();
	
	protected CaseSignificanceFixDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CaseSignificanceFixDriven fix = new CaseSignificanceFixDriven(null);
		try {
			ReportSheetManager.targetFolderId="1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			fix.additionalReportColumns = "change made, description now";
			fix.inputFileHasHeaderRow = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = false;  //We do that explicitly in the code
			fix.expectNullConcepts = true; //File contains lines that specify no change
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
		for (Description d : new ArrayList<>(c.getDescriptions())) {
			if (descChanges.containsKey(d.getId())) {
				CaseSignificance newSetting = SnomedUtils.translateCaseSignificanceToEnum(descChanges.get(d.getId()));
				String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				String after = SnomedUtils.translateCaseSignificanceFromEnum(newSetting);
				String transition = before + " -> " + after;
				
				//INFRA-4166 Addition - replace "OR" with "or" in descriptions
				if (d.getTerm().contains(" OR ")) {
					String newTerm = d.getTerm().replaceAll(" OR ", " or ");
					Description newDesc = replaceDescription(t, c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					newDesc.setCaseSignificance(newSetting);
					//Are we reusing an inactive description or adding a new one?
					if (StringUtils.isNumeric(newDesc.getDescriptionId())) {
						report (t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_REACTIVATED, transition, newDesc);
					} else {
						report (t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ADDED, transition, newDesc);
					}
					changesMade++;
				} else {
					if (d.getCaseSignificance().equals(newSetting)) {
						report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, transition, "CaseSignificance was already set to " + newSetting);
					} else {
						d.setCaseSignificance(newSetting);
						report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, transition, d);
						changesMade++;
					}
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
		//Don't make changes where current setting == new setting
		if (!lineItems[4].equals(lineItems[5])) {
			descChanges.put(lineItems[2], lineItems[5]);
			return Collections.singletonList(c);
		}
		return null;
	}
}
