package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * DRUGS-269
 * Lists all case sensitive terms that do not have capital letters after the first letter
 */
public class CaseSensitivity extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<Concept>();
	boolean unpublishedContentOnly = true;
	String[] properNouns = new String[] { "Doppler", "Lactobacillus", "Salmonella", "Staphylococcus", "Streptococcus", "X-linked" };
	//String[] knownLowerCase = new String[] { "milliliter" };
	Pattern numberLetter = Pattern.compile("\\d[a-z]");
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSensitivity report = new CaseSensitivity();
		try {
			report.additionalReportColumns = "description, isPreferred, caseSignificance, issue";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			info ("Producing case sensitivity report...");
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
					if (!unpublishedContentOnly || !d.isReleased()) {
						String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
						String firstLetter = d.getTerm().substring(0,1);
						String chopped = d.getTerm().substring(1);
						String preferred = d.isPreferred()?"Y":"N";
						//Lower case first letters must be entire term case sensitive
						if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
							report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
							incrementSummaryInformation("issues");
						} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
							if (chopped.equals(chopped.toLowerCase()) && 
									!letterFollowsNumber(d.getTerm()) && 
									!startsWithProperNoun(d.getTerm()) &&
									!c.getFsn().contains("(organism)")) {
								report (c, d, preferred, caseSig, "Case sensitive term does not have capital after first letter");
								incrementSummaryInformation("issues");
							}
						} else {
							//For case insensitive terms, we're on the look out for capitial letters after the first letter
							if (!chopped.equals(chopped.toLowerCase())) {
								report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
								incrementSummaryInformation("issues");
							}
						}
					}
				}
			}
		}
	}

	private boolean startsWithProperNoun(String term) {
		String firstWord = term.split(" ")[0];
		return ArrayUtils.contains(properNouns, firstWord);
	}

	private boolean letterFollowsNumber(String term) {
		//Do we have a letter following a number - optionally with a dash?
		term = term.replaceAll("-", "");
		Matcher matcher = numberLetter.matcher(term);
		return matcher.find();
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		//targetHierarchies.add(gl.getConcept("373873005")); // |Pharmaceutical / biologic product (product)|
		//targetHierarchies.add(gl.getConcept("105590001")); // |Substance (substance)|
		targetHierarchies.add(gl.getConcept("138875005")); // |SNOMED CT Concept (SNOMED RT+CTV3)|
	}

}
