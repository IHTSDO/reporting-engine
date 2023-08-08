package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * INFRA-5421 'Fix for 7672 instances of:
 * 'An active preferred term matching a FSN on an inactive concept must have same case significance'
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSignificanceFix_INFRA_5421 extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSignificanceFix_INFRA_5421.class);

	String[] knownLowerCase = new String[] { "mm", "nm" };
	CaseSensitivityUtils csUtils = new CaseSensitivityUtils();
	
	protected CaseSignificanceFix_INFRA_5421(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CaseSignificanceFix_INFRA_5421 fix = new CaseSignificanceFix_INFRA_5421(null);
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
	public void postInit() throws TermServerScriptException {
		csUtils.init();
		super.postInit();
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
		//Now, which term needs to be shifted?
		CaseSignificance fsnCaseSig = c.getFSNDescription().getCaseSignificance();
		//Is FSN correct?  What would an algorithm recommend?
		CaseSignificance fsnRecommendedCaseSig = csUtils.suggestCorrectCaseSignficance(c.getFSNDescription());
		
		if (fsnCaseSig.equals(fsnRecommendedCaseSig)) {
			String after = SnomedUtils.translateCaseSignificanceFromEnum(fsnRecommendedCaseSig);
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				if (d.getCaseSignificance().equals(fsnRecommendedCaseSig)) {
					continue;
				}
				String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				d.setCaseSignificance(fsnRecommendedCaseSig);
				report(t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, "Updated PT", before + " -> " + after, d);
				changesMade++;
			}
		} else {
			String before = SnomedUtils.translateCaseSignificanceFromEnum(fsnCaseSig);
			//In this case change the FSN to match the PT
			List<Description> pts = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
			if (pts == null || pts.size() == 0) {
				report (t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Preferred synonym not recovered");
				return NO_CHANGES_MADE;
			}
			
			if (pts.size() > 1 && !pts.get(0).getCaseSignificance().equals(pts.get(1).getCaseSignificance())) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Multiple PTs inconsistent CaseSig");
				for (Description d : pts) {
					if (!d.getCaseSignificance().equals(fsnRecommendedCaseSig)) {
						String after = SnomedUtils.translateCaseSignificanceFromEnum(fsnRecommendedCaseSig);
						d.setCaseSignificance(fsnRecommendedCaseSig);
						report(t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, "Updated PT", before + " -> " + after, d);
					}
				}
			}
			
			//Since they're the same, grab the case sig from the first one
			Description pt = pts.get(0);
			String after = SnomedUtils.translateCaseSignificanceFromEnum(pt.getCaseSignificance());
			c.getFSNDescription().setCaseSignificance(pt.getCaseSignificance());
			report(t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, "Updated FSN", before + " -> " + after, c.getFSNDescription());
			changesMade++;
			
		}
		return changesMade;
	}

	/*private CaseSignificance getRecommendedCaseSignificance(String term) {
		String firstLetter = term.substring(0,1);
		String chopped = term.substring(1);
		String firstWord = term.split(" ")[0];
		//Lower case first letters must be entire term case sensitive
		if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase())) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (chopped.equals(chopped.toLowerCase())) {
			//If the first word ends with 's then it's someone's name
			if (firstWord.contains("'s")) {
				return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			}
			return CaseSignificance.CASE_INSENSITIVE;
		} else if (!chopped.equals(chopped.toLowerCase())) {
			//Case insensitive term has a capital after first letter");
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		}
		throw new IllegalStateException("Unexpected Case Significance in " + term);
	}
	
	protected  List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept("41615007"));
	}*/

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();
		LOGGER.info ("Identifying incorrect case signficance settings");
		for (Concept c : gl.getAllConcepts()) {
			//Looking for PTs on inactive concept which matches FSN but does not have the same case sig
			if (!c.isActive()) {
				CaseSignificance fsnCaseSig = c.getFSNDescription().getCaseSignificance();
				String fsnPart = SnomedUtils.deconstructFSN(c.getFsn(), true)[0];
				Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
				if (usPT == null) {
					report((Task)null, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "No US PT found");
				} else if (usPT.getTerm().equals(fsnPart) && !usPT.getCaseSignificance().equals(fsnCaseSig)) {
					processMe.add(c);
				} else {
					Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
					if (gbPT == null) {
						report((Task)null, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "No GB PT found");
					}else if (gbPT.getTerm().equals(fsnPart) && !gbPT.getCaseSignificance().equals(fsnCaseSig)) {
						processMe.add(c);
					}
				}
			}
		}
		LOGGER.debug ("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}
}
