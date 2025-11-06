package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/**
 * LOINC-394 Inactivate LOINC concepts marked as deprecated
 */
public class InactivateLOINC extends BatchLoincFix {

	protected InactivateLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new InactivateLOINC(null).standardExecution(args);
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, Severity, Action, LOINC Num, Details, , , "};
		String[] tabNames = new String[] {"LOINC2020 Inactivations"};
		super.postInit(tabNames, columnHeadings, false);
		loadFiles();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			String loincNum = getLoincNumFromDescription(c);
			inactivateConcept(t, c, null, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, loincNum);
		} catch (ValidationFailure v) {
			report(t, c, v);
		} catch (Exception e) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE)) {
				String loincNum = getLoincNumFromDescription(c);
				String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
				if (thisStatus.equals(DISCOURAGED)) {
					componentsToProcess.add(c);
				}
			}
		}
		return componentsToProcess;
	}
}
