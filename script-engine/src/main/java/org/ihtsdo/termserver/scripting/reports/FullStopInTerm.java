package org.ihtsdo.termserver.scripting.reports;

import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * MAINT-224 Check for full stop in descriptions other than text definitions
 * Allow for numbers and abbreviations
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FullStopInTerm extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(FullStopInTerm.class);

	String[] knownAbbrevs = new String[] { "ser","ss","subsp",
								"f","E", "var", "St"};
	boolean reportConceptOnceOnly = true;
	final static String FULL_STOP = ".";
	
	public static void main(String[] args) throws TermServerScriptException {
		FullStopInTerm report = new FullStopInTerm();
		try {
			report.additionalReportColumns = "FSN, MatchedIn, Case, SubHierarchy, SubSubHierarchy";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportDescriptionContainsX();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void reportDescriptionContainsX() throws TermServerScriptException {
		nextConcept:
		for (Concept c : ROOT_CONCEPT.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				//We're only working on descriptions modified in the current release
				if (d.getEffectiveTime() != null) {
					continue;
				}
				boolean reported = false;
				if (d.getTerm().contains(FULL_STOP) && !allowableFullStop(d.getTerm())) {
					String[] hiearchies = getHierarchies(c);
					String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
					incrementSummaryInformation("Issues reported");
					report(c, d.getTerm(), cs, hiearchies[1], hiearchies[2]);
					reported = true;
				}
				if (reported && reportConceptOnceOnly) {
					continue nextConcept;
				}
			}
		}
	}

	private boolean allowableFullStop(String term) {
		//Work through all full stops in the term
		int index = term.indexOf(FULL_STOP);
		while (index >= 0) {
			boolean thisStopOK = false;
			//If the character after the full stop is a number, that's fine
			if (term.length() > index + 1 && Character.isDigit(term.charAt(index+1))) {
				thisStopOK = true;
			} else {
				for (String thisAbbrev : knownAbbrevs) {
					if ((index - thisAbbrev.length()) >= 0 && term.substring(index - thisAbbrev.length(), index).equals(thisAbbrev)) {
						thisStopOK = true;
						break;
					}
				}
			}
			
			if (thisStopOK) {
				index = term.indexOf(FULL_STOP, index + 1);
				continue;
			} else {
				return false;
			}
		}
		return true;
	}

	//Return hierarchy depths 1, 2, 3
	private String[] getHierarchies(Concept c) throws TermServerScriptException {
		String[] hierarchies = new String[3];
		Set<Concept> ancestors = c.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			int depth = ancestor.getDepth();
			if (depth > 0 && depth < 4) {
				hierarchies[depth -1] = ancestor.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			}
		}
		return hierarchies;
	}

}
