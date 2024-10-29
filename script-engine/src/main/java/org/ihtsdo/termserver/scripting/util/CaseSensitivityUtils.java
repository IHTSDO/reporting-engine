package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSensitivityUtils implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSensitivityUtils.class);

	private static final String[] GREEK_LETTERS_UPPER = new String[] { "Alpha", "Beta", "Delta", "Gamma", "Epsilon", "Tau" };
	private  static final String[] GREEK_LETTERS_LOWER = new String[] { "alpha", "beta", "delta", "gamma", "epsilon", "tau" };

	public static String[] getGreekLettersUpper() {
		return GREEK_LETTERS_UPPER;
	}

	public static String[] getGreekLettersLower() {
		return GREEK_LETTERS_LOWER;
	}

	private static CaseSensitivityUtils singleton;

	private final Map<String, Description> sourcesOfTruth = new HashMap<>();
	private final List<String> properNouns = new ArrayList<>();
	private final Map<String, List<String>> properNounPhrases = new HashMap<>();
	private final List<String> knownLowerCase = new ArrayList<>();
	private final Pattern numberLetter = Pattern.compile("\\d[a-z]");
	private final Pattern singleLetter = Pattern.compile("[^a-zA-Z][a-z][^a-zA-Z]");
	private final Set<String> wildcardWords = new HashSet<>();
	private final File inputFile = new File("resources/cs_words.tsv");
	private boolean substancesAndOrganismsAreSourcesOfTruth = true;
	private List<Concept> sourceOfTruthHierarchies;

	private static String[] taxonomyWordsArray = new String[] { "Clade","Class","Division",
			"Domain","Family","Genus","Infraclass",
			"Infraclass","Infrakingdom","Infraorder",
			"Infraorder","Kingdom","Order","Phylum",
			"Species","Subclass","Subdivision",
			"Subfamily","Subgenus","Subkingdom",
			"Suborder","Subphylum","Subspecies",
			"Superclass","Superdivision","Superfamily",
			"Superkingdom","Superorder"};

	private static Set<String> taxonomyWords = new HashSet<>(Arrays.asList(taxonomyWordsArray));

	public static CaseSensitivityUtils get() throws TermServerScriptException {
		return get(true);
	}

	public static CaseSensitivityUtils get(boolean substancesAndOrganismsAreSourcesOfTruth) throws TermServerScriptException {
		if (singleton == null) {
			singleton = new CaseSensitivityUtils();
			singleton.substancesAndOrganismsAreSourcesOfTruth = substancesAndOrganismsAreSourcesOfTruth;
			singleton.init();
		}
		return singleton;
	}

	public void init() throws TermServerScriptException {
		loadCSWords();

		if (substancesAndOrganismsAreSourcesOfTruth) {
			sourceOfTruthHierarchies = List.of(SUBSTANCE, ORGANISM);
			for (Concept sourceOfTruth : sourceOfTruthHierarchies) {
				processSourceOfTruth(sourceOfTruth);
			}
		} else {
			sourceOfTruthHierarchies = new ArrayList<>();
		}
	}

	public boolean isProperNoun(String word) {
		return properNouns.contains(word);
	}

	public boolean isSourceOfTruthHierarchy(Concept hierarchy) {
		return sourceOfTruthHierarchies.contains(hierarchy);
	}

	private void processSourceOfTruth(Concept sourceOfTruth) throws TermServerScriptException {
		LOGGER.info("Processing case sensitivity source of truth: {}", sourceOfTruth);
		for (Concept c : sourceOfTruth.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				String term = d.getTerm();
				if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					if (d.getType().equals(DescriptionType.FSN)) {
						term = SnomedUtilsBase.deconstructFSN(term)[0];
					}
					sourcesOfTruth.put(term, d);
				}

				//Now we might have a term like 107580008 |Family Fabaceae| and here we want to also match Fabaceae
				//So we'll add those noun phrases once the taxonomy words have been removed
				if (!d.getType().equals(DescriptionType.TEXT_DEFINITION)
						&& !d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
					addSourcesOfTruthWithoutTaxonomy(term, d);
				}
			}
		}
	}

	private void addSourcesOfTruthWithoutTaxonomy(String term, Description d) {
		String termWithoutTaxonomy = term;
		for (String taxonomyWord : taxonomyWords) {
			termWithoutTaxonomy = termWithoutTaxonomy.replace(taxonomyWord, "");
			termWithoutTaxonomy = termWithoutTaxonomy.replace(taxonomyWord.toLowerCase(), "");
		}

		if (!termWithoutTaxonomy.equals(term)) {
			termWithoutTaxonomy = termWithoutTaxonomy.replace("  ", " ").trim();
			sourcesOfTruth.put(termWithoutTaxonomy, d);
		}
	}

	public void loadCSWords() throws TermServerScriptException {
		LOGGER.info("Loading {}...", inputFile);
		if (!inputFile.canRead()) {
			throw new TermServerScriptException("Cannot read: " + inputFile);
		}
		List<String> lines;
		try {
			lines = Files.readLines(inputFile, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Failure while reading: " + inputFile, e);
		}
		LOGGER.info("Complete");
		LOGGER.info("Processing cs words file");
		for (String line : lines) {
			//Split the line on tabs
			String[] items = line.split(TAB);
			String phrase = items[0];
			//Does the word contain a capital letter (ie not the same as it's all lower case variant)
			if (!phrase.equals(phrase.toLowerCase())) {
				//Does this word end in a wildcard?
				if (phrase.endsWith("*")) {
					String wildWord = phrase.replace("*", "");
					wildcardWords.add(wildWord);
					continue;
				}
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

	public CaseSignificance suggestCorrectCaseSignficance(Description d) throws TermServerScriptException {
		String term = d.getTerm().replace("-", " ");
		String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		String firstLetter = term.substring(0,1);
		String chopped = term.substring(1);
		
		//Text Definitions must be CS
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
			//Lower case first letters must be entire term case-sensitive
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
			if (chopped.equals(chopped.toLowerCase()) && 
					!singleLetterCombo(term) && 
					!startsWithProperNounPhrase(term) &&
					!containsKnownLowerCaseWord(term)) {
				if (caseSig.equals(CS) && startsWithSingleLetter(d.getTerm())){
					//Probably OK
					return d.getCaseSignificance();
				} else {
					return CaseSignificance.CASE_INSENSITIVE;
				}
			}
		} else {
			if (startsWithProperNounPhrase(term)) {
				return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			}
			
			//Or if one of our sources of truth?
			String firstWord = d.getTerm().split(" ")[0];
			if (sourcesOfTruth.containsKey(firstWord)) {
				return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			}
			
			//For case-insensitive terms, we're on the look out for capital letters after the first letter
			if (!chopped.equals(chopped.toLowerCase())) {
				return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
			}
		}
		
		if (!chopped.equals(chopped.toLowerCase())) {
			//If the first character is a symbol then the initial letter is not case sensitive
			if (Character.isLetter(firstLetter.charAt(0))) {
				//Term has a capital after first letter
				return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			} else {
				return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
			}
		}
		
		return CaseSignificance.CASE_INSENSITIVE;
	}
	
	public boolean startsWithSingleLetter(String term) {
		if (Character.isLetter(term.charAt(0))) {
			//If it's only 1 character long, then yes! Otherwise, no!
			return (term.length() == 1 || !Character.isLetter(term.charAt(1)));
		}
		return false;
	}

	public boolean containsKnownLowerCaseWord(String term) {
		for (String lowerCaseWord : knownLowerCase) {
			if (term.equals(lowerCaseWord) || term.contains(" "  + lowerCaseWord + " ") || term.contains(" " + lowerCaseWord + "/") || term.contains("/" + lowerCaseWord + " ")) {
				return true;
			}
		}
		return false;
	}

	public boolean startsWithProperNounPhrase(String term) {
		String[] words = term.split(" ");
		String firstWord = words[0];
		
		if (properNouns.contains(firstWord)) {
			return true;
		}

		//Is the first word or the phrase one of our sources of truth?
		//This is a quick lookup, so do this first before we get into loops
		if (sourcesOfTruth.containsKey(firstWord) || sourcesOfTruth.containsKey(term)) {
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
		
		//Does the firstWord start with one of our wildcard words?
		for (String wildword : wildcardWords) {
			if (firstWord.startsWith(wildword)) {
				return true;
			}
		}

		//Work the number of words up progressively to see if we get a match 
		//eg first two words in "Influenza virus vaccine-containing product in nasal dose form" is an Organism
		StringBuilder progressive = new StringBuilder(firstWord);
		for (int i=1; i<words.length; i++) {
			progressive.append(" ").append(words[i]);
			if (sourcesOfTruth.containsKey(progressive.toString())) {
				return true;
			}
		}
		
		return false;
	}

	public boolean singleLetterCombo(String term) {
		//Do we have a letter following a number - optionally with a dash?
		term = term.replace("-", "");
		Matcher matcher = numberLetter.matcher(term);
		if (matcher.find()) {
			return true;
		}
		
		//A letter on it's own will often be lower case eg 3715305012 [768869001] US: P, GB: P: Interferon alfa-n3-containing product [cI]
		return singleLetter.matcher(term).find();
	}

	public Map<String, Description> getSourcesOfTruth() {
		return sourcesOfTruth;
	}
}
