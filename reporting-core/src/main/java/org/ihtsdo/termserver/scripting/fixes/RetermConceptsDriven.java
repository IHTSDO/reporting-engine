package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * LOINC-393
 * The description with the Correlation ID needs to updated for all concepts in that list from "Correlation ID:447559001" 
 * to "Correlation ID:447557004" (this will change the correlation from 447559001 |Broad to narrow map from SNOMED CT source code to target code (foundation metadata concept)| 
 * to 447557004 |Exact match map from SNOMED CT source code to target code (foundation metadata concept)|.
 */
public class RetermConceptsDriven extends BatchFix {
	
	Map<String, String> replacementMap;
	
	protected RetermConceptsDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RetermConceptsDriven fix = new RetermConceptsDriven(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = false;
			fix.runStandAlone = false;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		replacementMap = new HashMap<>();
		replacementMap.put("Correlation ID:447559001", "Correlation ID:447557004");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = reterm(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int reterm(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				//In this case we're looking for an entire match
				if (d.getTerm().equals(find)) {
					replaceDescription(t, c, d, replace, InactivationIndicator.OUTDATED);
					changesMade++;
				}
			}
		}
		return changesMade;
	}
}
