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
 * DRUGS-269, SUBST-130, MAINT-77
 * Lists all case sensitive terms that do not have capital letters after the first letter
 * UPDATE: We'll also load in the existing cs_words.txt file instead of hardcoding a list of proper nouns.
 */
public class CaseSensitivity extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<>();
	List<Concept> excludeHierarchies = new ArrayList<>();
	Set<Concept> allExclusions = new HashSet<>();
	boolean newlyModifiedContentOnly = true;
	List<String> properNouns = new ArrayList<>();
	Map<String, List<String>> properNounPhrases = new HashMap<>();
	List<String> knownLowerCase = new ArrayList<>();
	//String[] properNouns = new String[] { "Doppler", "Lactobacillus", "Salmonella", "Staphylococcus", "Streptococcus", "X-linked" };
	//String[] knownLowerCase = new String[] { "milliliter" };
	Pattern numberLetter = Pattern.compile("\\d[a-z]");
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSensitivity report = new CaseSensitivity();
		try {
			report.additionalReportColumns = "Semtag, description, isPreferred, caseSignificance, issue";
			//report.additionalReportColumns = "description, isPreferred, caseSignificance, usedInProduct, logicRuleOK, issue";
			report.init(args);
			report.loadCSWords();
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			info ("Producing case sensitivity report...");
			report.checkCaseSignificance();
			//report.checkCaseSignificanceSubstances();
		} finally {
			report.finish();
		}
	}
	
	private void postInit() throws TermServerScriptException {
		for (Concept excludeThis : excludeHierarchies) {
			excludeThis = gl.getConcept(excludeThis.getConceptId());
			allExclusions.addAll(excludeThis.getDescendents(NOT_SET));
		}
	}

private void loadCSWords() throws IOException, TermServerScriptException {
	info ("Loading " + inputFile);
	if (!inputFile.canRead()) {
		throw new TermServerScriptException ("Cannot read: " + inputFile);
	}
	List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
	for (String line : lines) {
		if (line.startsWith("milliunit/")) {
			debug ("Check here");
		}
		//Split the line up on tabs
		String[] items = line.split(TAB);
		String phrase = items[0];
		//Does the word contain a capital letter (ie not the same as it's all lower case variant)
		if (!phrase.equals(phrase.toLowerCase())) {
			//Is this a phrase?
			String[] words = phrase.split(" ");
			if (words.length == 1) {
				properNouns.add(phrase);
			} else {
				List<String> phrases = properNounPhrases.get(words[0]);
				if (phrases == null) {
					phrases = new ArrayList<>();
					properNounPhrases.put(words[0], phrases);
				}
				phrases.add(phrase);
			}
		} else {
			knownLowerCase.add(phrase);
		}
	}
}

	private void checkCaseSignificance() throws TermServerScriptException {
		//Work through all active descriptions of all hierarchies
		for (Concept targetHierarchy : targetHierarchies) {
			nextConcept:
			for (Concept c : targetHierarchy.getDescendents(NOT_SET)) {
				if (allExclusions.contains(c)) {
					continue;
				}
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getTerm().startsWith("Mirgorod")) {
						debug ("Check here");
					}
					if (!newlyModifiedContentOnly || !d.isReleased()) {
						String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
						String firstLetter = d.getTerm().substring(0,1);
						String chopped = d.getTerm().substring(1);
						String preferred = d.isPreferred()?"Y":"N";
						//Lower case first letters must be entire term case sensitive
						if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
							report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
							incrementSummaryInformation("issues");
							continue nextConcept;
						} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
							if (chopped.equals(chopped.toLowerCase()) && 
									!letterFollowsNumber(d.getTerm()) && 
									!startsWithProperNounPhrase(d.getTerm()) &&
									!containsKnownLowerCaseWord(d.getTerm())) {
								if (caseSig.equals(CS) && startsWithSingleLetter(d.getTerm())){
									//Probably OK
								} else {
									report (c, d, preferred, caseSig, "Case sensitive term does not have capital after first letter");
									incrementSummaryInformation("issues");
									continue nextConcept;
								}
							}
						} else {
							//For case insensitive terms, we're on the look out for capital letters after the first letter
							if (!chopped.equals(chopped.toLowerCase())) {
								report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
								incrementSummaryInformation("issues");
								continue nextConcept;
							}
						}
					}
				}
			}
		}
	}
	
	private boolean startsWithSingleLetter(String term) {
		if (Character.isLetter(term.charAt(0))) {
			//If it's only 1 character long, then yes!
			if (term.length() == 1 || !Character.isLetter(term.charAt(1))) {
				return true;
			} 
			return false;
		}
		return false;
	}

	private boolean containsKnownLowerCaseWord(String term) {
		for (String lowerCaseWord : knownLowerCase) {
			if (term.equals(lowerCaseWord) || term.contains(" "  + lowerCaseWord + " ") || term.contains(" " + lowerCaseWord + "/") || term.contains("/" + lowerCaseWord + " ")) {
				return true;
			}
		}
		return false;
	}
/*
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
	}*/

	private boolean startsWithProperNounPhrase(String term) {
		String firstWord = term.split(" ")[0];
		
		if (properNouns.contains(firstWord)) {
			return true;
		}
		//Also split on a slash
		firstWord = firstWord.split("/")[0];
		if (properNouns.contains(firstWord)) {
			return true;
		}

		//Could we match a noun phrase?
		if (properNounPhrases.containsKey(firstWord)) {
			for (String phrase : properNounPhrases.get(firstWord)) {
				if (term.startsWith(phrase)) {
					return true;
				}
			}
		}
		return false;
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
		targetHierarchies.add(ROOT_CONCEPT);
		//targetHierarchies.add(MEDICINAL_PRODUCT);
		
		excludeHierarchies.add(SUBSTANCE);
		excludeHierarchies.add(ORGANISM);
	}

}
