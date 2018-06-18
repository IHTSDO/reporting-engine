package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * DRUGS-269, SUBST-130
 * Lists all case sensitive terms that do not have capital letters after the first letter
 * UPDATE: We'll also load in the existing cs_words.txt file instead of hardcoding a list of proper nouns.
 */
public class CaseSensitivity extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<Concept>();
	boolean unpublishedContentOnly = false;
	List<String> properNouns = new ArrayList<>();
	List<String> knownLowerCase = new ArrayList<>();
	//String[] properNouns = new String[] { "Doppler", "Lactobacillus", "Salmonella", "Staphylococcus", "Streptococcus", "X-linked" };
	//String[] knownLowerCase = new String[] { "milliliter" };
	Pattern numberLetter = Pattern.compile("\\d[a-z]");
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSensitivity report = new CaseSensitivity();
		try {
			//report.additionalReportColumns = "description, isPreferred, caseSignificance, issue";
			report.additionalReportColumns = "description, isPreferred, caseSignificance, usedInProduct, logicRuleOK, issue";
			report.init(args);
			report.loadCSWords();
			report.loadProjectSnapshot(false);  //Load all descriptions
			info ("Producing case sensitivity report...");
			report.checkCaseSignificance();
			//report.checkCaseSignificanceSubstances();
		} finally {
			report.finish();
		}
	}

private void loadCSWords() throws IOException, TermServerScriptException {
	info ("Loading " + inputFile);
	if (!inputFile.canRead()) {
		throw new TermServerScriptException ("Cannot read: " + inputFile);
	}
	List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
	int lineNum = 0;
	for (String line : lines) {
		lineNum++;
		//Split the line up on spaces.  Expect two entries
		String[] items = line.split(" ");
		if (lineNum == 1) {
			//Skip headers
			continue;
		} else if (items.length > 2) {
			warn ("Extra item at line " + lineNum + ": " + line);
		} else if (items.length < 2) {
			warn ("Short line " + lineNum + ": " + line);
			continue;
		} else if (items[1].equals("2")) {
			//Status 2 items are not good, not sure what they're for.
			continue;
		}
		String word = items[0];
		//Does the word contain a capital letter (ie not the same as it's all lower case variant)
		if (!word.equals(word.toLowerCase())) {
			properNouns.add(word);
		} else {
			knownLowerCase.add(word);
		}
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
									!containsKnownLowerCaseWord(d.getTerm()) &&
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
	
	private boolean containsKnownLowerCaseWord(String term) {
		for (String lowerCaseWord : knownLowerCase) {
			if (term.contains(" "  + lowerCaseWord + " ") || term.contains(" " + lowerCaseWord + "/") || term.contains("/" + lowerCaseWord + " ")) {
				return true;
			}
		}
		return false;
	}

	private void checkCaseSignificanceSubstances() throws TermServerScriptException {
		Set<Concept> substancesUsedInProducts = getSubstancesUsedInProducts();
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			boolean usedInProduct = substancesUsedInProducts.contains(c);
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				String firstLetter = d.getTerm().substring(0,1);
				String chopped = d.getTerm().substring(1);
				String preferred = d.isPreferred()?"Y":"N";
				//Lower case first letters must be entire term case sensitive
				if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
					report (c, d, preferred, caseSig, (usedInProduct?"Y":"N"), "N", "Terms starting with lower case letter must be CS");
					incrementSummaryInformation("issues");
				} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
					if (chopped.equals(chopped.toLowerCase())) {
						boolean logicRuleOK = letterFollowsNumber(d.getTerm()) || startsWithProperNoun(d.getTerm());
						report (c, d, preferred, caseSig, (usedInProduct?"Y":"N"), (logicRuleOK?"Y":"N"), "Case sensitive term does not have capital after first letter");
						incrementSummaryInformation("issues");
					}
				} else {
					//For case insensitive terms, we're on the look out for capitial letters after the first letter
					if (!chopped.equals(chopped.toLowerCase())) {
						report (c, d, preferred, caseSig, (usedInProduct?"Y":"N"), "N", "Case insensitive term has a capital after first letter");
						incrementSummaryInformation("issues");
					}
				}
			}
		}
	}

	private Set<Concept> getSubstancesUsedInProducts() throws TermServerScriptException {
		Set<Concept> substancesUsedInProducts = new HashSet<>();
		for (Concept product : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
		}
		return substancesUsedInProducts;
	}

	private boolean startsWithProperNoun(String term) {
		String firstWord = term.split(" ")[0];
		return properNouns.contains(firstWord);
	}

	private boolean letterFollowsNumber(String term) {
		//Do we have a letter following a number - optionally with a dash?
		term = term.replaceAll("-", "");
		Matcher matcher = numberLetter.matcher(term);
		return matcher.find();
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		//targetHierarchies.add(PHARM_BIO_PRODUCT);
		//targetHierarchies.add(SUBSTANCE);
		//targetHierarchies.add(ROOT_CONCEPT);
		targetHierarchies.add(MEDICINAL_PRODUCT);
	}

}
