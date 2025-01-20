package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * LOINC-389
 * Reterm concepts as driven by file
 */
public class RetermConceptsDrivenDistinct extends BatchFix {
	
	Map<Concept, String> currentExpectedFSN = new HashMap<>();
	Map<Concept, String> replacementTermMap = new HashMap<>();
	String semTag = " (observable entity)";
	
	protected RetermConceptsDrivenDistinct(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RetermConceptsDrivenDistinct fix = new RetermConceptsDrivenDistinct(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = false;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
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
		//Is this the current term that we're expecting?
		if (!c.getFsn().equals(currentExpectedFSN.get(c))) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "FSN specified does not match expectations", currentExpectedFSN.get(c));
			return changesMade;
		}
		
		Description fsn = c.getFSNDescription();
		String newFSN = replacementTermMap.get(c) + semTag;
		replaceDescription(t, c, fsn, newFSN, InactivationIndicator.OUTDATED);
		changesMade++;
		
		//Check we've only got one preferred term
		List<Description> PTs = c.getPreferredSynonyms();
		if (PTs.size() > 1) {
			String ptStr = PTs.stream().map(d -> d.getTerm()).collect(Collectors.joining(",\n"));
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple PTs Detected", ptStr);
			return changesMade;
		}
		replaceDescription(t, c, PTs.get(0), replacementTermMap.get(c), InactivationIndicator.OUTDATED);
		changesMade++;
		return changesMade;
	}
	
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		String sctId = lineItems[0];
		String currentFSN = lineItems[1];
		String newTerm = lineItems[2];
		Concept c = gl.getConcept(sctId);
		currentExpectedFSN.put(c, currentFSN);
		replacementTermMap.put(c, newTerm);
		return Collections.singletonList(c);
	}
}
