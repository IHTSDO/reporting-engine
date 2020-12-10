package org.ihtsdo.termserver.scripting.fixes.qi;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.StringUtils;

/**
 * SCTQA-321 reaplce teletherapy with "external beam radiation therapy" in FSNs
 */
public class RetermFSN extends BatchFix {

	private Map<String, String> termMap = new HashMap<>();
	
	protected RetermFSN(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RetermFSN fix = new RetermFSN(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
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
		termMap.put("teletherapy", "external beam radiation therapy");
		termMap.put("teleradiotherapy", "external beam radiation therapy");
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
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			switch (d.getType()) {
				case FSN : changesMade += modifyFSN(t, c);
							break;
				/*case SYNONYM :  if (!isExcluded(d.getTerm().toLowerCase())) {
									String replacement = d.getTerm() + " with contrast";
									replaceDescription(t, c, d, replacement, null);
									changesMade++;
								};
								break;*/
				default : 
			}
		}
		return changesMade;
	}

	private int modifyFSN(Task t, Concept c) throws TermServerScriptException {
		String replacement = c.getFsn();
		for (String term : termMap.keySet()) {
			if (c.getFsn().contains(term)) {
				replacement = c.getFsn().replace(term, termMap.get(term));
				break;
			} else {
				//Try a capitalized version
				String capTerm = StringUtils.capitalizeFirstLetter(term);
				if (c.getFsn().contains(capTerm)) {
					String capReplacement =  StringUtils.capitalizeFirstLetter(termMap.get(term));
					replacement = c.getFsn().replace(capTerm, capReplacement);
					break;
				}
			}
		}
		if (replacement.equals(c.getFsn())) {
			debug("Failed to replace term in " + c);
		}
		replaceDescription(t, c, c.getFSNDescription(), replacement, null);
		return CHANGE_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts()
				.stream()
				.filter(c -> c.isActive())
				.filter(c -> containsMatchingTerm(c))
				.collect(Collectors.toList());
	}

	private boolean containsMatchingTerm(Concept c) {
		String fsn = c.getFsn().toLowerCase();
		for (String term : termMap.keySet()) {
			if (fsn.contains(term)) {
				return true;
			}
		}
		return false;
	}
}
