package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * MSSP-1169 Fix DK Capitalization issues
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSignificanceFixForLanguage extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSignificanceFixForLanguage.class);

	private List<String> exceptions = new ArrayList<>();
	private boolean expectFirstLetterCapitalization = false;
	private String longDash = Character.toString((char)150);
	private List<Component> knownEntireTermCaseSensitive;
	private int skippedDueToNotStartingWithLetter = 0;
	
	public static void main(String[] args) throws TermServerScriptException {
		CaseSignificanceFixForLanguage delta = new CaseSignificanceFixForLanguage();
		try {
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions\
			delta.additionalReportColumns = "FSN, SemTag, Severity, Action, Description, Old, New, Notes, ";
			delta.languageCode = "nl";
			delta.edition="NL";
			delta.runStandAlone = false;
			delta.init(args);
			delta.loadProjectSnapshot(false); 
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.startTimer();
			delta.process();
			if (!delta.dryRun) {
				delta.flushFiles(false); //Need to flush files before zipping
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Concept SCTID,FSN, SemTag, Severity, Action, Description, Old, New, Notes",
				"Concept SCTID,FSN, SemTag, Severity, Action, Description, Old, New, Notes",
				"Concept SCTID,FSN, SemTag, Severity, Action, Description, Old, New, Notes"};
		String[] tabNames = new String[] {
				"Changed",
				"Unchanged",
				"Special"};
		super.postInit(googleFolder, tabNames, columnHeadings);
		gl.setAllComponentsClean();
	}

	@Override
	protected void process() throws TermServerScriptException {
		LOGGER.info("Processing...");
		
		knownEntireTermCaseSensitive = processFile();
		
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
		
		LOGGER.info("Processing Complete");
		LOGGER.info("Skipped " + skippedDueToNotStartingWithLetter + " due to not starting with a letter");
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Description d = gl.getDescription(lineItems[0]);
		return List.of(d);
	}

	public int normalizeCaseSignificance(Concept c, boolean aggressive) throws TermServerScriptException {
		int changesMade = 0;
		if (exceptions.contains(c.getId())) {
			report(c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "", "","","Concept manually listed as an exception");
		} else {
			for (Description d : c.getDescriptions(languageCode, ActiveState.ACTIVE)) {
				char firstChar = d.getTerm().charAt(0);
				//Decided to skip terms that don't start with a letter, for now
				if (!StringUtils.isLetter(firstChar)) {
					skippedDueToNotStartingWithLetter++;
					continue;
				}
				if (exceptions.contains(c.getId())) {
					report(c, Severity.MEDIUM, ReportActionType.NO_CHANGE, d, "","","Description manually listed as an exception");
				} else {
					changesMade += funnySymbolSwap(c, d);
					try {
						changesMade += normalizeCaseSignificance(c, d);
					} catch (Exception e) {
						report(c, Severity.CRITICAL, ReportActionType.UNEXPECTED_CONDITION, d, e);
					}
					/*changesMade += checkForCaptitalizedEnglishWord(c, d);
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
					}*/
				}
			}
		}
		return changesMade;
	}
	
	/**
	 * This function follows the Dutch rules:
	 * If the description is listed as known rule in supplied file set to CS
	 * If the term starts with a capital letter or a digit, it should always be marked as 'entire sensitive'.
	 * If the term does not contain any upper case letters at all, it should be marked 'entire insensitive'.
	 * If the term starts with a character that is not a letter or digit, e.g. 'WHO performance status' graad 1 (which starts with a quotation mark), you should look at the next character that is a letter or digit; so this particular example should be marked 'entire sensitive'.
	 * The terms in the file I attached to this issue (Entire-sensitive-exceptions.txt) should be marked as 'entire sensitive'. I have added the description Id, concept Id and term itself.
	 * All other terms (i.e. starting with lowercase letter, containing an uppercase letter, and not listed as an exception) should be marked 'initial character insensitive'.
	 * @throws TermServerScriptException 
	 */
	private int normalizeCaseSignificance(Concept c, Description d) throws TermServerScriptException {
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			return NO_CHANGES_MADE;
		}
		
		if (knownEntireTermCaseSensitive.contains(d)) {
			return setCaseSignificanceIfRequired(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, c, d);
		}
		
		char firstChar = d.getTerm().charAt(0);
		//char secondChar = d.getTerm().charAt(1);
		//We might have a succession of non-alpha characters, so find the first letter
		Character firstLetter = StringUtils.getFirstLetter(d.getTerm());
		boolean firstCharIsAlpha = StringUtils.isLetter(firstChar);
		boolean firstCharIsDigit = StringUtils.isDigit(firstChar);
		String firstWord = StringUtils.getFirstWord(d.getTerm(), true);
		
		//Special case for substances like 11q22.2q22.3-microdeletiesyndroom and 2-ethylhexylacrylaat
		//Find starts with number and then check for number letter in first word
		if (firstCharIsDigit) {
			//We're going to ignore dashes in this situation
			String firstWordIgnoreDashes = StringUtils.getFirstWord(d.getTerm().replaceAll("-", ""));
			if (StringUtils.isMixAlphaNumeric(firstWordIgnoreDashes)) {
				return setCaseSignificanceIfRequired(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, c, d);
			} else {
				report(TERTIARY_REPORT, c, Severity.NONE, ReportActionType.VALIDATION_CHECK, d);
			}
		}
		
		if (firstLetter == null) {
			//If we have no letters then we must be case insensitive
			return setCaseSignificanceIfRequired(CaseSignificance.CASE_INSENSITIVE, c, d);
		} else if (firstCharIsAlpha && StringUtils.isCapitalized(firstChar)) {
			return setCaseSignificanceIfRequired(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, c, d);
		} else if (!firstCharIsAlpha && StringUtils.isCapitalized(firstLetter)) {
			/*if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
				return NO_CHANGES_MADE;
			}
			return setCaseSignificanceIfRequired(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE, c, d);*/
			return setCaseSignificanceIfRequired(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, c, d);
		} else if (!StringUtils.isCaseSensitive(d.getTerm(), false)) {
			return setCaseSignificanceIfRequired(CaseSignificance.CASE_INSENSITIVE, c, d);
		} else if (!StringUtils.isCapitalized(firstChar) && StringUtils.isMixedCase(firstWord)) {
			return setCaseSignificanceIfRequired(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE, c, d);
		} else if (!StringUtils.isCapitalized(firstChar) && StringUtils.isCaseSensitive(d.getTerm(), false)) {
			return setCaseSignificanceIfRequired(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE, c, d);
		} else {
			throw new IllegalStateException("Coding error - unexpected condition for " + d);
		}
	}

	private int setCaseSignificanceIfRequired(CaseSignificance caseSig, Concept c, Description d) throws TermServerScriptException {
		if (d.getCaseSignificance().equals(caseSig)) {
			boolean skip = caseSig.equals(CaseSignificance.CASE_INSENSITIVE) && !StringUtils.isCaseSensitive(d.getTerm(), false);
			if (!skip) {
				//report(SECONDARY_REPORT, c, Severity.NONE, ReportActionType.NO_CHANGE, d);
			}
			return NO_CHANGES_MADE;
		} else {
			String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
			String after = SnomedUtils.translateCaseSignificanceFromEnum(caseSig);
			report(c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, before,after);
			d.setCaseSignificance(caseSig);
			d.setEffectiveTime(null);
			d.setDirty();
			c.setModified();
			return CHANGE_MADE;
		}
	}

	private int funnySymbolSwap(Concept c, Description d) throws TermServerScriptException {
		if (d.getTerm().contains(longDash)) {
			String oldTerm = d.getTerm();
			String newTerm = oldTerm.replaceAll(longDash, "-");
			d.setTerm(newTerm);
			d.setEffectiveTime(null);
			d.setDirty();
			c.setModified();
			report(c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, d, "","","Mangled character replaced with '-', was " + oldTerm);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}
	

	/**
	 * During translation, it might be that some English words are retained and, if these
	 * were the first word in the term might also retain a capital letter
	 * eg 4190381000005117 [273311005] da: batteriet Behavior assessment [cI] 
	 * Where an English word is found as NOT the first word, as long as the 
	 * original Term is case insensitive, then we can decapitalize the translation.
	 * @throws TermServerScriptException 
	 */
	private int checkForCaptitalizedEnglishWord(Concept c, Description d) throws TermServerScriptException {
		String[] words = d.getTerm().split(" ");
		boolean isFirst = true;
		for (String word : words) {
			if (isFirst) {
				isFirst = false;
				continue;
			}
			if (StringUtils.isCapitalized(word) && existsAsCaseInsensitiveEnglishFirstWord(c, word)) {
				String oldTerm = d.getTerm();
				String newTerm = d.getTerm().replace(word, word.toLowerCase());
				d.setTerm(newTerm);
				d.setEffectiveTime(null);
				d.setDirty();
				c.setModified();
				report(c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, d, "","","Decapitalized non-first English word.  Was: " + oldTerm);
				return CHANGE_MADE;
			}
		}
		return NO_CHANGES_MADE;
	}

	private boolean existsAsCaseInsensitiveEnglishFirstWord(Concept c, String word) {
		for (Description d : c.getDescriptions("en", ActiveState.ACTIVE)) {
			if (d.getTerm().startsWith(word) 
					&& d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
				return true;
			}
		}
		return false;
	}

	private boolean alignWithEnglishIfSameFirstWord(Concept c, Description d) throws TermServerScriptException {
		String firstWord = StringUtils.getFirstWord(d.getTerm());
		String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		for (Description checkMe : c.getDescriptions("en", ActiveState.ACTIVE)) {
			if (!checkMe.equals(d) 
					&& StringUtils.getFirstWord(checkMe.getTerm()).equalsIgnoreCase(firstWord) 
					&& !d.getCaseSignificance().equals(checkMe.getCaseSignificance())) {
				
				//Now if the English was cI but the Danish has no capital letters, the we want to 
				//keep the Danish as case insensitive
				if (checkMe.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)
						&& !expectFirstLetterCapitalization && !StringUtils.isCaseSensitive(d.getTerm(),expectFirstLetterCapitalization)) {
					continue;
				}
				
				//If the English was case insensitive but the Danish has ADDED capital letters, then we're
				//thinking the first letter is the only one that's case insensitive.
				if (checkMe.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)
						&& !expectFirstLetterCapitalization && StringUtils.isCaseSensitive(d.getTerm(),expectFirstLetterCapitalization)) {
					continue;
				}
				
				String after = SnomedUtils.translateCaseSignificanceFromEnum(checkMe.getCaseSignificance());
				d.setCaseSignificance(checkMe.getCaseSignificance());
				d.setEffectiveTime(null);
				d.setDirty();
				c.setModified();
				incrementSummaryInformation("Descriptions modified", 1);
				report(c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, before,after,"Case Sigificance aligned with:" + checkMe);
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
		
		//Is this word just entirely lower case and that's expected?
		if (!expectFirstLetterCapitalization && !StringUtils.isCaseSensitive(term,expectFirstLetterCapitalization)) {
			d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
			d.setEffectiveTime(null);
			d.setDirty();
			changesMade++;
			c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			report(c, Severity.LOW,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "cI", "ci", d.getEffectiveTimeSafely());
			incrementSummaryInformation("Descriptions modified", 1);
		} else if (expectFirstLetterCapitalization && StringUtils.initialLetterLowerCase(term)) {
			//First letter lower case should always be CS
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
