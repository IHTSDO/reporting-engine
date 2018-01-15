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
public class CaseSensitivity extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<Concept>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSensitivity report = new CaseSensitivity();
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
					String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
					String firstLetter = d.getTerm().substring(0,1);
					String chopped = d.getTerm().substring(1);
					//Lower case first letters must be entire term case sensitive
					if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
						report (c, d, caseSig, "Terms starting with lower case letter must be CS");
						incrementSummaryInformation("issues");
					} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
						if (chopped.equals(chopped.toLowerCase())) {
							report (c, d, caseSig, "Case sensitive term does not have capital after first letter");
							incrementSummaryInformation("issues");
						}
					} else {
						//For case insensitive terms, we're on the look out for capitial letters after the first letter
						if (!chopped.equals(chopped.toLowerCase())) {
							report (c, d, caseSig, "Case insensitive term has a capital after first letter");
							incrementSummaryInformation("issues");
						}
					}
				}
			}
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
		writeToReportFile(sb.toString());
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		targetHierarchies.add(gl.getConcept("373873005")); // |Pharmaceutical / biologic product (product)|
		targetHierarchies.add(gl.getConcept("105590001")); // |Substance (substance)|
		writeToReportFile("concept, fsn, descId, caseSignificance, issue, description");
	}

}
