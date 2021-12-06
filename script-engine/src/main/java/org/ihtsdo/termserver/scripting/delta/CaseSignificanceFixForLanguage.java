package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * MSSP-1169 Fix DK Capitalization issues
 */
public class CaseSignificanceFixForLanguage extends DeltaGenerator implements ScriptConstants {
	
	private List<String> exceptions = new ArrayList<>();
	private boolean expectFirstLetterCapitalization = false;
	private String longDash = Character.toString((char)150);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CaseSignificanceFixForLanguage delta = new CaseSignificanceFixForLanguage();
		try {
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions\
			delta.additionalReportColumns = "FSN, SemTag, Severity, Action, Description, Old, New, Notes, ";
			delta.languageCode = "da";
			delta.edition="DK";
			delta.runStandAlone = false;
			delta.init(args);
			delta.loadProjectSnapshot(false); 
			delta.postInit();
			delta.startTimer();
			delta.process();
			if (!dryRun) {
				delta.flushFiles(false, true); //Need to flush files before zipping
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
		}
	}
	
	private void process() throws TermServerScriptException {
		info("Processing...");
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.isActive()) {
				normalizeCaseSignificance(c, false);
			}
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified", 1);
				if (!dryRun) {
					outputRF2(c);  //Will only output dirty fields.
				}
			}
		}
	}

	public int normalizeCaseSignificance(Concept c, boolean aggressive) throws TermServerScriptException {
		int changesMade = 0;
		if (exceptions.contains(c.getId())) {
			report (c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "", "","","Concept manually listed as an exception");
		} else {
			for (Description d : c.getDescriptions(languageCode, ActiveState.ACTIVE)) {
				if (exceptions.contains(c.getId())) {
					report (c, Severity.MEDIUM, ReportActionType.NO_CHANGE, d, "","","Description manually listed as an exception");
				} else {
					changesMade += funnySymbolSwap(c, d);
					//Requirement to align with English as first check
					if (!alignWithEnglishIfSameFirstWord(c, d)) {
						switch (d.getCaseSignificance()) {
							case INITIAL_CHARACTER_CASE_INSENSITIVE : changesMade += normalizeCaseSignificance_cI(c, d);
																		break;
							case CASE_INSENSITIVE : changesMade += normalizeCaseSignificance_ci(c, d);
																		break;
							case ENTIRE_TERM_CASE_SENSITIVE:  //Have to assume author is correct here, unless we're being aggressive
														if (aggressive) {
															changesMade += normalizeCaseSignificance_CS(c, d);
														}
														break;
						}
					} else {
						changesMade += 1;
					}
				}
			}
		}
		return changesMade;
	}

	private int funnySymbolSwap(Concept c, Description d) throws TermServerScriptException {
		if (d.getTerm().contains(longDash)) {
			String oldTerm = d.getTerm();
			String newTerm = oldTerm.replaceAll(longDash, "-");
			d.setTerm(newTerm);
			d.setEffectiveTime(null);
			d.setDirty();
			report (c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, d, "","","Mangled character replaced with '-', was " + oldTerm);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}

	private boolean alignWithEnglishIfSameFirstWord(Concept c, Description d) throws TermServerScriptException {
		String firstWord = StringUtils.getFirstWord(d.getTerm());
		String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		for (Description checkMe : c.getDescriptions("en", ActiveState.ACTIVE)) {
			if (!checkMe.equals(d) 
					&& StringUtils.getFirstWord(checkMe.getTerm()).equalsIgnoreCase(firstWord) 
					&& !d.getCaseSignificance().equals(checkMe.getCaseSignificance())) {
				String after = SnomedUtils.translateCaseSignificanceFromEnum(checkMe.getCaseSignificance());
				d.setCaseSignificance(checkMe.getCaseSignificance());
				d.setEffectiveTime(null);
				d.setDirty();
				c.setModified();
				incrementSummaryInformation("Descriptions modified", 1);
				report (c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, before,after,"Case Sigificance aligned with:" + checkMe);
				return true;
			}
		}
		return false;
	}

	private int normalizeCaseSignificance_cI(Concept c, Description d) throws TermServerScriptException {
		int changesMade = 0;
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtils.deconstructFSN(term)[0];
		}
		//First letter lower case should always be CS
		if (StringUtils.initialLetterLowerCase(term)) {
			d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			d.setEffectiveTime(null);
			d.setDirty();
			changesMade++;
			c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			report(c, Severity.LOW,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "cI", "CS", d.getEffectiveTimeSafely());
			incrementSummaryInformation("Descriptions modified", 1);
		} else if (!StringUtils.isCaseSensitive(term,expectFirstLetterCapitalization)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, d, "", "", "Confirm that term contains lower case, case significant letters");
		}
		return changesMade;
	}
	
	private int normalizeCaseSignificance_ci(Concept c, Description d) throws TermServerScriptException {
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtils.deconstructFSN(term)[0];
		}
		boolean changeMade = false;
		//First letter lower case should always be CS
		//Commenting this out for DK, they allow their first letter to be lower case
		/*if (StringUtils.initialLetterLowerCase(term)) {
			d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			changeMade = true;
		} else*/ if (StringUtils.isCaseSensitive(term,expectFirstLetterCapitalization)) {
			d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			changeMade = true;
		}
		
		if (changeMade) {
			String newValue = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
			d.setEffectiveTime(null);
			d.setDirty();
			c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			report(c,Severity.LOW,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "ci", newValue);
			incrementSummaryInformation("Descriptions modified", 1);
		}
		return changeMade ? 1 : 0;
	}
	
	/**
	 * Here we're going to say that if we ONLY start with an upper case letter then there's been 
	 * a mistake and we can make the entire term case insensitive
	 * @param c
	 * @param d
	 * @param silent 
	 * @throws TermServerScriptException 
	 */
	private int normalizeCaseSignificance_CS(Concept c, Description d) throws TermServerScriptException {
		int changesMade = 0;
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtils.deconstructFSN(term)[0];
		}
		//First letter lower case should always be CS, so no change.  
		//Otherwise if we've no further upper case letters, we *could* be ok to say this
		//is "ci", but only if we're being aggressive about it.
		if (!StringUtils.initialLetterLowerCase(term) && !StringUtils.isCaseSensitive(term,expectFirstLetterCapitalization)) {
				d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
				d.setEffectiveTime(null);
				d.setDirty();
				changesMade++;
				c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			report(c,Severity.MEDIUM,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "CS", "ci");
			incrementSummaryInformation("Descriptions modified", 1);
		}
		return changesMade;
	}

}
