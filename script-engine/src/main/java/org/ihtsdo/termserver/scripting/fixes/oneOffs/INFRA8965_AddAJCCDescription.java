package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-8965 Add additional description to AJCC Cancer Concepts
 */
public class INFRA8965_AddAJCCDescription extends BatchFix {

	private Set<String> exclusionTexts;
	private CaseSignificance defaultCaseSig = CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
	
	String prefix = "AJCC (American Joint Committee on Cancer) ";
	
	protected INFRA8965_AddAJCCDescription(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA8965_AddAJCCDescription fix = new INFRA8965_AddAJCCDescription(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
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
		subsetECL = "<< 1222584008 |American Joint Committee on Cancer allowable value (qualifier value)|";
		exclusionTexts = new HashSet<>();
		exclusionTexts.add("federation of gynecology and obstetrics");
		exclusionTexts.add("pathological N category allowable value");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = addDescription(task, loadedConcept);
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

	private int addDescription(Task t, Concept c) throws TermServerScriptException {
		String pt = c.getPreferredSynonym();
		if (pt.contains("American Joint Committee on Cancer")) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "PT did not look like XX, skipping");
			return NO_CHANGES_MADE;
		}
		String newTerm = prefix + pt;
		Description d = Description.withDefaults(newTerm, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		//TODO For this run we know all the terms need to be CS.  Other runs might need more discerning 
		d.setCaseSignificance(defaultCaseSig);
		addDescription(t, c, d);
		return CHANGE_MADE;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		return isExcluded(fsn);
	}
	
	private boolean isExcluded(String term) {
		for (String exclusionWord : exclusionTexts) {
			if (term.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return findConcepts(subsetECL)
				.stream()
				.filter(c -> !isExcluded(c))
				.collect(Collectors.toList());
	}
}
