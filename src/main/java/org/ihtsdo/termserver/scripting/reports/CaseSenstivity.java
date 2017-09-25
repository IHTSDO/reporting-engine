package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Lists all case sensitive terms that do not have capital letters after the first letter
 */
public class CaseSenstivity extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<Concept>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSenstivity report = new CaseSenstivity();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			println ("Producing case sensitivity report...");
			report.checkCaseSignificance();
		} finally {
			report.finish();
		}
	}

	private void checkCaseSignificance() throws TermServerScriptException {
		//Work through all active descriptions of all hierarchies
		for (Concept targetHierarchy : targetHierarchies) {
			for (Concept c : targetHierarchy.getDescendents(NOT_SET)) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getCaseSignificance().equals(SCTID_ENTIRE_TERM_CASE_SENSITIVE) || d.getCaseSignificance().equals(SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE)) {
						valiateDescriptionCS(c,d);
					}
				}
			}
		}
	}
	
	private void valiateDescriptionCS(Concept c, Description d) throws TermServerScriptException {
		//This is a case sensitive term.
		//If it doesn't have any capital letters after the first term, then it potentially doesn't need to be.
		//Chop off the first letter, then see if the lower case equals the original
		String chopped = d.getTerm().substring(1);
		if (chopped.equals(chopped.toLowerCase())) {
			String caseSig = SnomedUtils.translateCaseSignificanceFromSctId(d.getCaseSignificance());
			report (c, d, caseSig, "Case sensitive term does not have capital after first letter");
		}
	}

	private void report(Concept c, Description d, String caseSig, String issue) {
		StringBuffer sb = new StringBuffer();
		sb.append(c.getConceptId()).append(COMMA_QUOTE)
			.append(c.getFsn()).append(QUOTE_COMMA)
			.append(d.getDescriptionId()).append(COMMA_QUOTE)
			.append(caseSig).append(QUOTE_COMMA_QUOTE)
			.append(issue).append(QUOTE_COMMA_QUOTE)
			.append(d.getTerm()).append(QUOTE);
		writeToFile(sb.toString());
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		targetHierarchies.add(gl.getConcept("373873005")); // |Pharmaceutical / biologic product (product)|
		targetHierarchies.add(gl.getConcept("105590001")); // |Substance (substance)|
		writeToFile("concept, fsn, descId, caseSignificance, issue, description");
	}

}
