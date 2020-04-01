package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.StringUtils;

/*
 * INFRA-4166 Modify case significance from CS to cI where descriptions starts with non-alpha character
 */
public class CaseSignificanceFix_INFRA_4166 extends BatchFix implements RF2Constants{
	
	String[] knownLowerCase = new String[] { "mm", "nm" };
	
	protected CaseSignificanceFix_INFRA_4166(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CaseSignificanceFix_INFRA_4166 fix = new CaseSignificanceFix_INFRA_4166(null);
		try {
			ReportSheetManager.targetFolderId="1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			fix.additionalReportColumns = "recommend ci, description";
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
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
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) && startsWithNonAlpha(d.getTerm())) {
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
				boolean ci = recommendCaseInsensitive(d.getTerm());
				report (t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, ci?"Y":"N", d);
				changesMade++;
			} 
		}
		return changesMade;
	}

	private boolean recommendCaseInsensitive(String term) {
		//If the term changes in being made lower case, then it's case sensitive
		if (!term.equals(term.toLowerCase())) {
			return false;
		}
		
		for (String str : knownLowerCase) {
			if (term.contains(str)) {
				return false;
			}
		}
		
		//To be on the safe side, we will leave single lower case letters alone eg /s
		if (StringUtils.containsSingleLowerCaseLetter(term)) {
			return false;
		}
		
		return true;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();
		info ("Identifying incorrect case signficance settings");
		for (Concept concept : gl.getAllConcepts()) {
			if (concept.isActive()) {
				for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) &&
							startsWithNonAlpha(d.getTerm())) {
						processMe.add(concept);
						break;
					}
				}
			}
		}
		debug ("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}

	private boolean startsWithNonAlpha(String term) {
		return !Character.isLetter(term.charAt(0));
	}
}
