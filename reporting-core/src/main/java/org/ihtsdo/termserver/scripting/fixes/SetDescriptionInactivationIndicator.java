package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-5204 Add "NCEP" description inactivation indicator where not present
 */
public class SetDescriptionInactivationIndicator extends BatchFix {
	
	InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;

	protected SetDescriptionInactivationIndicator(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SetDescriptionInactivationIndicator fix = new SetDescriptionInactivationIndicator(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//INFRA-5204
		subsetECL = "<< 420040002|Fluoroscopic angiography (procedure)|";
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
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

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			if (d.getInactivationIndicator()==null) {
				d.setInactivationIndicator(inactivationIndicator);
				changesMade++;
				report(t, c, Severity.LOW, ReportActionType.INACT_IND_ADDED, d, inactivationIndicator);
			} else if (StringUtils.isEmpty(d.getEffectiveTime())) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, d, "Recently inactivated as", d.getInactivationIndicator());
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, d, "Previously inactivated as", d.getInactivationIndicator());
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return findConcepts(subsetECL)
				.stream()
				.collect(Collectors.toList());
	}
}
