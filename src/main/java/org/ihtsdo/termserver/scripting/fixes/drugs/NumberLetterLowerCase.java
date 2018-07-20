package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.reports.CaseSensitivity;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
SUBST-279 Where a term starts with a number and then a capital letter follows, 
then as long as that word is not itself case sensitive (as per cs_words.txt)
then make it small.
Then check against case significance rules to see if it can be made "ci"
*/
public class NumberLetterLowerCase extends DrugBatchFix implements RF2Constants{
	
	CaseSensitivity csReport;
	
	protected NumberLetterLowerCase(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		NumberLetterLowerCase fix = new NumberLetterLowerCase(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.init(args);
			fix.loadProjectSnapshot(false); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		csReport = new CaseSensitivity(this);
		csReport.loadCSWords();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		try {
			int changes = normaliseCase(task, loadedConcept);
			if (changes > 0) {
				updateConcept(task, loadedConcept, info);
			}
			return changes;
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return NO_CHANGES_MADE;
	}

	public int normaliseCase(Task t, Concept c) throws TermServerScriptException {
		int changes = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			changes += normaliseCase(t, c, d);
		}
		return changes;
	}

	private int normaliseCase(Task t, Concept c, Description d) {
		String term = d.getTerm();
		//Work through the letters of the term
		for (int i=0; i < term.length(); i++) {
			//Have we encountered a letter?  If so, is it the first one?
			if (Character.isLetter(term.charAt(i))) {
				//Not interested in terms that start with a letter, or 
				//where the first letter is lower case
				if (i == 0 || Character.isLowerCase(term.charAt(i))) {
					return NO_CHANGES_MADE;
				} else {
					//OK so here we have a capital letter thats not the 
					//first in the term.  Is it a known case sensitive word?
					String subString = term.substring(i);
					if (csReport.singleCapital(subString)) {
						report (t, c, Severity.MEDIUM, ReportActionType.INFO, d, "Skipping term - single captial letter");
						return NO_CHANGES_MADE;
					} else if (csReport.startsWithProperNounPhrase(subString)) {
						report (t, c, Severity.MEDIUM, ReportActionType.INFO, d, "Skipping term - known cs word");
						return NO_CHANGES_MADE;
					} else {
						//If the NEXT character is not a letter, or is also capital, then skip
						if (!Character.isLetter(term.charAt(i+1)) || Character.isUpperCase(term.charAt(i+1))) {
							report (t, c, Severity.MEDIUM, ReportActionType.INFO, d, "Skipping term - capital is not part of word");
							return NO_CHANGES_MADE;
						}
						StringBuilder modifiedTerm = new StringBuilder(term);
						modifiedTerm.setCharAt(i, Character.toLowerCase(term.charAt(i)));
						Description newDesc = replaceDescription(t, c, d, modifiedTerm.toString(), InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
						setCaseSignificance(t, c, newDesc);
						incrementSummaryInformation("Captial switched with lower case letter");
						return CHANGE_MADE;
					}
				}
			}
		}
		return NO_CHANGES_MADE;
	}

	private void setCaseSignificance(Task t, Concept c, Description d) {
		if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) ||
			d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
			if (SnomedUtils.isCaseSensitive(d.getTerm())) {
				report(t,c, Severity.LOW, ReportActionType.INFO, d, "term contains capital - retaining case sensitivity");
				incrementSummaryInformation("CS retained due to capital");
			} else if (csReport.containsKnownLowerCaseWord(d.getTerm())) {
				report(t,c, Severity.LOW, ReportActionType.INFO, d, "term contains known lower case word - retaining case sensitivity");
				incrementSummaryInformation("CS retained due to forced lower case word");
			} else {
				d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
				report(t,c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d);
				incrementSummaryInformation("CS/cI changed to ci");
			}
		}
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();
		setQuiet(true);
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			//Work with a clone of the concept so we don't fix the issues on the initial pass
			c = c.cloneWithIds();
			//if (c.getConceptId().equals("86884000")) {
			if (c.getConceptId().equals("18344000")) {
				debug ("Here");
			}
			
			if (normaliseCase(null, c) > 0) {
				processMe.add(c);
			}
		}
		setQuiet(false);
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return asComponents(processMe);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
}
