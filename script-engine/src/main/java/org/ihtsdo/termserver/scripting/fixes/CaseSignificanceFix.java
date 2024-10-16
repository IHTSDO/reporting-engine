package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;


/**
 * Addresses case significance issues in the target sub-hierarchy
 * 
 * SUBST-288 Remove capital letter from greek letters (unless initial letter)
 * and adjust CS as required. 
 * 
 * SUBST-289 Many substance terms have a single letter - either a single lower 
 * case letter or a single upper case letter - case sensitivity needs to reflect these.
 **/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSignificanceFix extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSignificanceFix.class);

	boolean unpublishedContentOnly = false;
	Concept subHierarchy = SUBSTANCE;

	String[] exceptions = new String[] {"86622001", "710898000", "116559002"};
	String[] exceptionStr = new String[] {"Alphavirus", "Taur"};
	
	CaseSensitivityUtils csUtils;
	
	protected CaseSignificanceFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CaseSignificanceFix fix = new CaseSignificanceFix(null);
		try {
			ReportSheetManager.targetFolderId = "1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d"; //SUBSTANCES
			fix.selfDetermining = true;
			fix.init(args);
			fix.csUtils = CaseSensitivityUtils.get();
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		//int changesMade = fixCaseSignifianceIssues(task, loadedConcept);
		//int changesMade = fixGreekLetterIssues(task, loadedConcept);
		int changesMade = fixSingleLetterIssues(task, loadedConcept);
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
		}
		return changesMade;
	}
	
	private int fixCaseSignifianceIssues(Task task, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if ( (!unpublishedContentOnly || !d.isReleased()) &&
					d.getTerm().contains("Product containing") && d.getTerm().contains("milliliter")) {
				String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				String firstLetter = d.getTerm().substring(0,1);
				String chopped = d.getTerm().substring(1);
				//Lower case first letters must be entire term case sensitive
				if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
					//Not dealing with this situation right now
					//report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
				} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
					if (chopped.equals(chopped.toLowerCase())) {
						report (task, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "-> ci" );
						d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
						changesMade++;
					}
				} else {
					//For case insensitive terms, we're on the look out for capitial letters after the first letter
					if (!chopped.equals(chopped.toLowerCase())) {
						//Not dealing with this situation right now
						//report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
						//incrementSummaryInformation("issues");
					}
				}
			}
		}
		return changesMade;
	}
	
	private int fixSingleLetterIssues(Task task, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
			String firstLetter = d.getTerm().substring(0,1);
			String firstWord = d.getTerm().split(" ")[0];
			
			if (!caseSig.equals(CS) && firstLetterSingle(d.getTerm())) {
				report (task, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, caseSig + "-> CS" );
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				changesMade++;
			} else if (!caseSig.equals(cI) && StringUtils.containsSingleLetter(d.getTerm())) {
				if (caseSig.equals(CS)) {
					//If we start with a small letter, single letter or a proper noun, that's fine
					if (!firstLetter.equals(firstLetter.toLowerCase()) 
							&& !csUtils.isProperNoun(firstWord)
							&& !csUtils.startsWithProperNounPhrase(c, d.getTerm())
							&& !firstLetterSingle(d.getTerm())) {
						report (task, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, caseSig + "-> cI" );
						d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
						changesMade++;
					} 
				} else {
					report (task, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, caseSig + "-> cI" );
					d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
					changesMade++;
				}
			}
		}
		return changesMade;
	}
	
	private boolean firstLetterSingle(String term) {
		return Character.isLetter(term.charAt(0)) && (term.length() == 1 || !Character.isLetter(term.charAt(1)));
	}
	
	private int fixGreekLetterIssues(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		if (c.getConceptId().equals("102777002")) {
			LOGGER.debug("Check Here");
		}
		boolean greekLetterFound = false;
		
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String matchedGreekUpper = StringUtils.containsAny(d.getTerm(), CaseSensitivityUtils.getGreekLettersUpper());
			String matchedGreekLower = StringUtils.containsAny(d.getTerm(), CaseSensitivityUtils.getGreekLettersLower());
			if ( (matchedGreekUpper != null || matchedGreekLower != null) && 
				(!unpublishedContentOnly || !d.isReleased())) {
				greekLetterFound = true;
				Description checkTerm = d;
				
				//If we start with the greek letter capitalised, that's OK to be capital
				if (matchedGreekUpper != null && !d.getTerm().startsWith(matchedGreekUpper)) {
					String replacementTerm = d.getTerm().replaceAll(matchedGreekUpper, matchedGreekUpper.toLowerCase());
					checkTerm = replaceDescription(t, c, d, replacementTerm, InactivationIndicator.ERRONEOUS);
					changesMade++;
				}
				
				//If we START with the greek letter lower, that's needs to be capitalised
				if (matchedGreekLower != null && d.getTerm().startsWith(matchedGreekLower)) {
					String replacementTerm = StringUtils.capitalize(d.getTerm());
					//Now we might have an erroneous capital after a dash, or after "< ", say within 5 characters
					for (int idx = matchedGreekLower.length() ; idx + 2 < replacementTerm.length() && idx < matchedGreekLower.length() + 5; idx ++) {
						if (replacementTerm.charAt(idx) == '-' && replacementTerm.charAt(idx+1) == Character.toUpperCase(replacementTerm.charAt(idx+1))) {
							//if the NEXT character is also a dash, then leave this eg alpha-L-fucosidase
							//OR if it's also a capital eg alpha-MT
							if (replacementTerm.charAt(idx+2) != '-' && replacementTerm.charAt(idx+2) != Character.toUpperCase(replacementTerm.charAt(idx+2))) {
								replacementTerm = replacementTerm.substring(0,idx +1)+ Character.toLowerCase(replacementTerm.charAt(idx+1)) +replacementTerm.substring(idx+2);
							}
						}
						
						if (replacementTerm.charAt(idx) == ' ' && replacementTerm.charAt(idx+1) == Character.toUpperCase(replacementTerm.charAt(idx+1))) {
							replacementTerm = replacementTerm.substring(0,idx +1)+ Character.toLowerCase(replacementTerm.charAt(idx+1)) +replacementTerm.substring(idx+2);
						}
						
						if (replacementTerm.charAt(idx) == '<' && replacementTerm.charAt(idx +1) == ' ' && replacementTerm.charAt(idx+2) == Character.toUpperCase(replacementTerm.charAt(idx+2))) {
							replacementTerm = replacementTerm.substring(0,idx +2)+ Character.toLowerCase(replacementTerm.charAt(idx+2)) +replacementTerm.substring(idx+3);
						}
					}
					checkTerm = replaceDescription(t, c, d, replacementTerm, InactivationIndicator.ERRONEOUS);
					changesMade++;
				}
				
				String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(checkTerm.getCaseSignificance());
				String firstLetter = checkTerm.getTerm().substring(0,1);
				String firstWord = checkTerm.getTerm().split(" ")[0];
				String chopped = checkTerm.getTerm().substring(1);
				//Lower case first letters must be entire term case sensitive
				if (!checkTerm.getTerm().startsWith("Hb ") && !checkTerm.getTerm().startsWith("T-") && checkTerm.getTerm().charAt(1) != '-') {
					if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
						//Not dealing with this situation right now
						//report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
					} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
						if (chopped.equals(chopped.toLowerCase()) && !csUtils.isProperNoun(firstWord)) {
							report (t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, checkTerm, caseSig + "-> ci" );
							checkTerm.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
							changesMade++;
						} else if (caseSig.equals(CS)){
							//Might be CS when doesn't need to be
							if (!csUtils.isProperNoun(firstWord)) {
								report (t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, checkTerm, caseSig + "-> cI" );
								checkTerm.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
								changesMade++;
							}
						}
					} else {
						//For case insensitive terms, we're on the look out for capitial letters after the first letter
						if (!chopped.equals(chopped.toLowerCase())) {
							report (t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, checkTerm, caseSig + "-> cI" );
							checkTerm.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
							changesMade++;
						}
					}
				}
			}
		}
		if (greekLetterFound && changesMade == 0) {
			incrementSummaryInformationQuiet("Greek letter concept with no changes required");
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();
		LOGGER.info ("Identifying incorrect case signficance settings");
		this.setQuiet(true);
		for (Concept concept : subHierarchy.getDescendants(NOT_SET)) {
			/*if (!concept.getConceptId().equals("130867000")) {
				continue;
			}*/
			if (concept.isActive() && !isException(concept.getId()) && !isExceptionStr(concept.getFsn())) {
				/*if (fixCaseSignifianceIssues(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				}*/
				/*if (fixGreekLetterIssues(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				} */
				if (fixSingleLetterIssues(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				}
			}
		}
		LOGGER.debug ("Identified " + processMe.size() + " concepts to process");
		this.setQuiet(false);
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}

	private boolean isExceptionStr(String fsn) {
		for (String str : exceptionStr) {
			if (fsn.contains(str)) {
				return true;
			}
		}
		return false;
	}

	private boolean isException(String id) {
		for (String exception : exceptions) {
			if (exception.equals(id)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
