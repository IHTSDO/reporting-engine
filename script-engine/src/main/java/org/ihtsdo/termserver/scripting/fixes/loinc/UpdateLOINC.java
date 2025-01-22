package org.ihtsdo.termserver.scripting.fixes.loinc;


import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;


/**
 * LOINC-394 Inactivate LOINC concepts marked as deprecated
 */
public class UpdateLOINC extends BatchLoincFix {

	protected UpdateLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		UpdateLOINC fix = new UpdateLOINC(null);
		try {
			ReportSheetManager.setTargetFolderId("1yF2g_YsNBepOukAu2vO0PICqJMAyURwh");  //LOINC
			fix.populateEditPanel = false;
			fix.reportNoChange = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.stateComponentType = false;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, SemTag, Severity, Action, Detail, Details, , , "};
		String[] tabNames = new String[] {"LOINC2020 Inactivations"};
		super.postInit(tabNames, columnHeadings, false);
		loadFiles();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			inactivateConcept(task, concept, null, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

}
