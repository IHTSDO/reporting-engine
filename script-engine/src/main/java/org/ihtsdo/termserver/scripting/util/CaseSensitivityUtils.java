package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;

import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSensitivityUtils implements ScriptConstants {
	
	private static final String CS_WORDS_FILE = "cs_words.tsv";
	private static final int LOWER_CASE_COUNT_FOR_NOT_ACRONYM = 4;

	private enum CaseSensitiveSourceOfTruthType {
		SUBSTANCE, ORGANISM, CS_WORDS_FILE, LANGUAGE, RELIGION, EPONYM,
		PROPER_NOUN_FROM_CS_WORDS_FILE, KNOWN_LOWER_CASE_FROM_CS_WORDS_FILE,
		KNOWN_LOWER_CASE_SOURCE_OF_TRUTH
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSensitivityUtils.class);

	public static final String FORCE_CS = "FORCE_CS";

	private static final String[] GREEK_LETTERS_UPPER = new String[] { "Alpha", "Beta", "Delta", "Gamma", "Epsilon", "Tau" };
	private  static final String[] GREEK_LETTERS_LOWER = new String[] { "alpha", "beta", "delta", "gamma", "epsilon", "tau" };

	public static String[] getGreekLettersUpper() {
		return GREEK_LETTERS_UPPER;
	}

	public static String[] getGreekLettersLower() {
		return GREEK_LETTERS_LOWER;
	}

	private static CaseSensitivityUtils singleton;

	private final Map<Concept, List<String>> csInContext = new HashMap<>();
	private final Pattern numberLetter = Pattern.compile("\\d[a-z]");
	private final Pattern singleLetter = Pattern.compile("[^a-zA-Z][a-z][^a-zA-Z]");
	private final Set<String> wildcardWords = new HashSet<>();
	private final File inputFile = new File("resources/cs_words.tsv");
	private boolean substancesAndOrganismsAreSourcesOfTruth = true;
	private List<Concept> sourceOfTruthHierarchies;

	private Map<CaseSensitiveSourceOfTruthType, Object> caseSensitiveSourceOfTruthMap = new EnumMap<>(CaseSensitiveSourceOfTruthType.class);

	private List<String> caseInsensitivePrefix = List.of("Non-", "Pseudo-", "Hyper-", "Anti-");

	private static String[] taxonomyWordsArray = new String[] {
			"Clade","Class","Division",
			"Domain","Family","Genus","Infraclass",
			"Infraclass","Infrakingdom","Infraorder",
			"Infraorder","Kingdom","Order","Phylum",
			"Species","Subclass","Subdivision",
			"Subfamily","Subgenus","Subkingdom",
			"Suborder","Subphylum","Subspecies",
			"Superclass","Superdivision","Superfamily",
			"Superkingdom","Superorder"};

	private static Set<String> taxonomyWords = new HashSet<>(Arrays.asList(taxonomyWordsArray));

	private static List<String> exceptionsToEponyms = List.of("Mother's", "Father's");

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

	public static boolean isciorcI(Description d) {
		return d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) ||
				d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
	}

	public void init() throws TermServerScriptException {
		GraphLoader gl = GraphLoader.getGraphLoader();
		loadCSWords();
		determineEponyms();
		if (substancesAndOrganismsAreSourcesOfTruth) {
			sourceOfTruthHierarchies = List.of(SUBSTANCE,
					ORGANISM,
					gl.getConcept("297289008 |World languages|"),
					gl.getConcept("108334009 |Religion AND/OR philosophy|"));
			for (Concept sourceOfTruth : sourceOfTruthHierarchies) {
				processSourceOfTruth(sourceOfTruth);
			}
		} else {
			sourceOfTruthHierarchies = new ArrayList<>();
		}
	}

	private void determineEponyms() {
		//Words that contain X's indicate someone's name
		LOGGER.info("Determining eponyms (known names / proper nouns)");
		Map<String, Concept> knownNames = new HashMap<>();
		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (c.isActiveSafely()) {
				checkConceptForEponyms(c, knownNames);
			}
		}
		LOGGER.info("{} eponyms detected", knownNames.size());
		caseSensitiveSourceOfTruthMap.put(CaseSensitiveSourceOfTruthType.EPONYM, knownNames);
	}

	private void checkConceptForEponyms(Concept c, Map<String, Concept> knownNames) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			for (String word : d.getTerm().split(" ")) {
				if (!exceptionsToEponyms.contains(word) && (word.endsWith("'s") || word.endsWith("s'"))) {
					String wordWithoutApostrophe = trimApostrophe(word);
					if (wordWithoutApostrophe.startsWith("(")) {
						wordWithoutApostrophe = word.substring(1);
					}
					knownNames.put(wordWithoutApostrophe, c);
				}
			}
		}
	}

	private String trimApostrophe(String word) {
		if (word.endsWith("'s")) {
			return word.substring(0, word.length() - 2);
		} else if (word.endsWith("s'")) {
			return word.substring(0, word.length() - 1);
		} else {
			throw new IllegalArgumentException("Word does not end with 's or s': " + word);
		}
	}

	public boolean isSourceOfTruthHierarchy(Concept hierarchy) {
		return sourceOfTruthHierarchies.contains(hierarchy);
	}

	private void processSourceOfTruth(Concept sourceOfTruth) throws TermServerScriptException {
		LOGGER.info("Processing case sensitive source of truth: {}", sourceOfTruth);
		for (Concept c : sourceOfTruth.getDescendants(NOT_SET)) {
			if (c.getId().equals("31006001")) {
				LOGGER.debug("Processing source of truth: {}", c);
			}
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				if (!d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
					processDescriptionSourceOfTruth(c, d);
				}
			}
		}
	}

	private void processDescriptionSourceOfTruth(Concept c, Description d) {
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtilsBase.deconstructFSN(term)[0];
		}
		
		if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
			csInContext.computeIfAbsent(c, k -> new ArrayList<>()).add(term);
		}

		//Now we might have a term like 107580008 |Family Fabaceae| and here we want to also match Fabaceae
		//So we'll add those noun phrases once the taxonomy words have been removed
		if (!d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
			addSourcesOfTruthWithoutTaxonomy(c, term);
		}
	}

	public boolean isTaxonomicWord(String word) {
		return taxonomyWords.contains(word);
	}

	private void addSourcesOfTruthWithoutTaxonomy(Concept c, String term) {
		String termWithoutTaxonomy = term;
		for (String taxonomyWord : taxonomyWords) {
			termWithoutTaxonomy = termWithoutTaxonomy.replace(taxonomyWord, "");
			termWithoutTaxonomy = termWithoutTaxonomy.replace(taxonomyWord.toLowerCase(), "");
		}

		if (!termWithoutTaxonomy.equals(term)) {
			termWithoutTaxonomy = termWithoutTaxonomy.replace("  ", " ").trim();
			List<String> csWords = csInContext.computeIfAbsent(c, k -> new ArrayList<>());
			if (!csWords.contains(termWithoutTaxonomy)) {
				csWords.add(termWithoutTaxonomy);
			}
		}

		//If the term is entirely lower case, then we can add that as a separate tracked source of truth
		if (term.equals(term.toLowerCase())) {
			List<String> csWords = getCaseSensitiveSourceOfTruth(CaseSensitiveSourceOfTruthType.KNOWN_LOWER_CASE_SOURCE_OF_TRUTH);
			if (!csWords.contains(term)) {
				csWords.add(term);
			}
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

		LOGGER.info("Processing cs words file");
		for (String line : lines) {
			loadCSWord(line);
		}
	}

	private void loadCSWord(String line) {
		//Split the line on tabs
		String[] items = line.split(TAB);
		String phrase = items[0].trim();
		//Does the word contain a capital letter (ie not the same as it's all lower case variant)
		if (!phrase.equals(phrase.toLowerCase())) {
			//Does this word end in a wildcard?
			if (phrase.endsWith("*")) {
				String wildWord = phrase.replace("*", "");
				wildcardWords.add(wildWord);
				return;
			}
			getCaseSensitiveSourceOfTruth(CaseSensitiveSourceOfTruthType.PROPER_NOUN_FROM_CS_WORDS_FILE).add(phrase);
		} else {
			getCaseSensitiveSourceOfTruth(CaseSensitiveSourceOfTruthType.KNOWN_LOWER_CASE_FROM_CS_WORDS_FILE).add(phrase);
		}
	}

	private List<String> getCaseSensitiveSourceOfTruth(CaseSensitiveSourceOfTruthType type) {
		return (List<String>) caseSensitiveSourceOfTruthMap.computeIfAbsent(type, k -> new ArrayList<String>());
	}

	public CaseSignificance suggestCorrectCaseSignificance(Concept context, Description d) throws TermServerScriptException {
		//Have we set a flag for override?
		if (d.hasIssue(FORCE_CS)) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		}

		String term = d.getTerm().replace("-", " ");
		String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		String firstLetter = term.substring(0,1);
		String chopped = term.substring(1);
		
		//Text Definitions must be CS.  Also for descriptions that start with an acronym
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION) || startsWithAcronym(term)) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
			//Lower case first letters must be entire term case-sensitive
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
			CaseSignificance cs = suggestCorrectCaseSignificanceForCaseSensitiveTerm(context, d, chopped, caseSig, term);
			if (cs != null) {
				return cs;
			}
		} else {
			CaseSignificance cs = suggestCorrectCaseSignificanceForCaseInSensitiveTerm(context, d, chopped, term);
			if (cs != null) {
				return cs;
			}
		}

		if (!chopped.equals(chopped.toLowerCase())) {
			return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
		}
		
		return CaseSignificance.CASE_INSENSITIVE;
	}

	private CaseSignificance suggestCorrectCaseSignificanceForCaseInSensitiveTerm(Concept context, Description d, String chopped,
	                                                                              String term) {
		if (startsWithKnownCaseSensitiveTerm(context, term)) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		}

		//Or if one of our sources of truth?
		String firstWord = d.getTerm().split(" ")[0];
		if (startsWithKnownCsWordInContext(context, firstWord, null)) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		}

		//For case-insensitive terms, we're on the look-out for capital letters after the first letter
		if (!chopped.equals(chopped.toLowerCase())) {
			return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
		}
		return null;
	}

	private CaseSignificance suggestCorrectCaseSignificanceForCaseSensitiveTerm(Concept context, Description d, String chopped,
	                                                                            String caseSig, String term) {
		if (chopped.equals(chopped.toLowerCase()) &&
				!singleLetterCombo(term) &&
				!startsWithKnownCaseSensitiveTerm(context, term) &&
				!containsKnownLowerCaseWord(term)) {
			if (caseSig.equals(CS) && startsWithSingleLetter(d.getTerm())){
				//Probably OK
				return d.getCaseSignificance();
			} else {
				return CaseSignificance.CASE_INSENSITIVE;
			}
		}
		return null;
	}
	
	public boolean startsWithSingleLetter(String term) {
		if (Character.isLetter(term.charAt(0))) {
			//If it's only 1 character long, then yes! Otherwise, no!
			return (term.length() == 1 || !Character.isLetter(term.charAt(1)));
		}
		return false;
	}

	public boolean containsKnownLowerCaseWord(String term) {
		for (String word : term.split(" ")) {
			if (word.equals(word.toLowerCase()) &&
					(getCaseSensitiveSourceOfTruth(CaseSensitiveSourceOfTruthType.KNOWN_LOWER_CASE_FROM_CS_WORDS_FILE).contains(word))
			|| getCaseSensitiveSourceOfTruth(CaseSensitiveSourceOfTruthType.KNOWN_LOWER_CASE_SOURCE_OF_TRUTH).contains(word)) {
				return true;
			}
		}
		return false;
	}

	public boolean startsWithAcronym(String term) {
		if (StringUtils.isEmpty(term)) {
			LOGGER.warn("Check here");
			return false;
		}
		String firstWord = term.split(" ")[0];
		//We were initially checking just the first and second characters, but also something like IgE
		//should be class as an acronym also
		boolean hasUpperCaseAfterFirstLetter = false;
		boolean hasLowerCaseAfterFirstLetter = false;

		//We're also going to check that if we have more than LOWER_CASE_COUNT_FOR_NOT_ACRONYM lower case letters in a row, then this isn't an acronym
		int lowerCaseLettersInARow = 0;

		for (int i = 1; i < firstWord.length(); i++) {
			char c = firstWord.charAt(i);

			//How many lower case letters in a row do we have?
			if (Character.isLowerCase(c)) {
				lowerCaseLettersInARow++;
				if (lowerCaseLettersInARow >= LOWER_CASE_COUNT_FOR_NOT_ACRONYM) {
					return false;
				}
			} else {
				lowerCaseLettersInARow = 0;
			}

			//If we get to a slash and the only capital encountered was the first letter, then we can stop checking for acronym eg Sperms/mL
			if (c == '/' && !hasUpperCaseAfterFirstLetter) {
				return false;
			}

			if ((c == '-' || c=='/') && i + 1 < firstWord.length() && Character.isUpperCase(firstWord.charAt(i + 1))) {
				i++; // Skip the dash and the capital letter. This is a repeat of sentence capitalization.
				continue;
			}

			if (Character.isUpperCase(c)) {
				hasUpperCaseAfterFirstLetter = true;
			} else if (Character.isLowerCase(c)) {
				hasLowerCaseAfterFirstLetter = true;
			}
		}

		// An acronym must have at least one uppercase letter after the first character,
		// ensuring it's not just a capitalized regular word.
		return hasUpperCaseAfterFirstLetter && (hasLowerCaseAfterFirstLetter || firstWord.equals(firstWord.toUpperCase()));
	}

	public boolean startsWithKnownCaseSensitiveTerm(Concept context, String term) {
		String[] words = term.split(" ");
		String firstWord = words[0];
		
		if (checkSourcesOfTruthForCSWord(firstWord)) {
			return true;
		}

		//Is the first word or the phrase one of our sources of truth?
		//This is a quick lookup, so do this first before we get into loops
		if (isCsSourceOfTruth(context) || startsWithKnownCsWordInContext(context, firstWord, term)) {
			return true;
		}

		//Also split on a slash
		firstWord = firstWord.split("/")[0];
		if (checkSourcesOfTruthForCSWord(firstWord)) {
			return true;
		}

		if (startsWithWildcardWord(firstWord)) {
			return true;
		}


		//Work the number of words up progressively to see if we get a match 
		//eg first two words in "Influenza virus vaccine-containing product in nasal dose form" is an Organism
		StringBuilder progressive = new StringBuilder(firstWord);
		for (int i=1; i<words.length; i++) {
			progressive.append(" ").append(words[i]);
			if (startsWithKnownCsWordInContext(context, null, progressive.toString())) {
				return true;
			}
		}

		//If the first word contains a dash, then also check that first part word without the dash
		if (firstWord.contains("-")) {
			return startsWithKnownCaseSensitiveTerm(context, term.replace("-", " "));
		}
		
		return false;
	}

	public boolean isCsSourceOfTruth(Concept c) {
		return csInContext.containsKey(c);
	}

	private boolean checkSourcesOfTruthForCSWord(String word) {
		for (Map.Entry<CaseSensitiveSourceOfTruthType, Object> entry : caseSensitiveSourceOfTruthMap.entrySet()) {
			Object sourceOfTruthObj = entry.getValue();
			if (sourceOfTruthObj instanceof Collection<?> sourceOfTruth) {
				if (sourceOfTruth.contains(word)) {
					return true;
				}
			} else if (sourceOfTruthObj instanceof Map) {
				Map<?, ?> sourceOfTruth = (Map<?, ?>) sourceOfTruthObj;
				if (sourceOfTruth.containsKey(word)) {
					return true;
				}
			}
		}

		//Are we 's or s' ?  trim that off and check again
		return ((word.endsWith("'s") || word.endsWith("s'"))
			&& checkSourcesOfTruthForCSWord(trimApostrophe(word)));
	}

	public boolean startsWithKnownCsWordInContext(Concept context, String firstWord, String term) {
		//The context is the concept that the term is being used in
		//We're interested if any of its attributes are a source of truth
		for (Concept attributeValue : SnomedUtils.getTargets(context)) {
			if (csInContext.containsKey(attributeValue)) {
				for (String knownCsWord : csInContext.get(attributeValue)) {
					if ((firstWord != null && knownCsWord.equals(firstWord))
							|| knownCsWord.equals(term)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public String explainCsWordInContext(Concept context, String word) {
		for (Concept attributeValue : SnomedUtils.getTargets(context)) {
			if (csInContext.containsKey(attributeValue)) {
				for (String knownCsWord : csInContext.get(attributeValue)) {
					if (knownCsWord.contains(word)) {
						return findCsDescriptionFeaturingWord(attributeValue, word);
					}
				}
			}
		}
		return "No description found";
	}

	private String findCsDescriptionFeaturingWord(Concept c, String word) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().contains(word)) {
				return c.toString() +  " : " + d.toString();
			}
		}
		return "No description found";
	}

	private boolean startsWithWildcardWord(String firstWord) {
		//Does the firstWord start with one of our wildcard words?
		for (String wildword : wildcardWords) {
			if (firstWord.startsWith(wildword)) {
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
		
		//A letter on its own will often be lower case
		//eg 3715305012 [768869001] US: P, GB: P: Interferon alfa-n3-containing product [cI]
		return singleLetter.matcher(term).find();
	}

	public boolean startsWithNumberOrSymbol(String term) {
		//Does the term start with something that is not a unicode letter?
		return term.substring(0,1).matches("[^\\p{L}]+");
	}

	public boolean startsWithLowerCaseLetter(String term) {
		//Does the first character start with a lower case letter?
		return Character.isAlphabetic(term.charAt(0)) && Character.isLowerCase(term.charAt(0));
	}

	public boolean termIsSingleWord(String term) {
		return term.split(" ").length == 1;
	}

	public class KnowledgeSource {
		String category;
		String reference;

		public KnowledgeSource(String category, String reference) {
			this.category = category;
			this.reference = reference;
		}

		public String toString()  {
			return category + ": " + reference;
		}

		public String getReference() {
			return reference;
		}

		public String getCategory() {
			return category;
		}
	}


	public Map<String, Set<KnowledgeSource>> explainEverything() {
		Map<String, Set<KnowledgeSource>> everything = new TreeMap<>();
		//Add everything we know about, and where it came from
		for (Map.Entry<CaseSensitiveSourceOfTruthType, Object> entry : caseSensitiveSourceOfTruthMap.entrySet()) {
			String source = entry.getKey().name();
			Object structure = entry.getValue();
			if (structure instanceof Map<?, ?> mapStructure) {
				for (Map.Entry<?, ?> structureEntry : mapStructure.entrySet()) {
					populateKnowledgeSource(everything, (String)structureEntry.getKey(), source, structureEntry.getValue().toString());
				}
			} else if (structure instanceof List<?> listStructure) {
				for (Object word : listStructure) {
					populateKnowledgeSource(everything, (String)word, source, "");
				}
			} else {
				LOGGER.warn("Unknown structure type: {}", structure);
			}
		}
		
		for (String word : wildcardWords) {
			populateKnowledgeSource(everything, word, "Wildcard Word", CS_WORDS_FILE);
		}

		for (Map.Entry<Concept, List<String>> entry : csInContext.entrySet()) {
			for (String word : entry.getValue()) {
				populateKnowledgeSource(everything, word, "Source of Truth", findCsDescriptionFeaturingWord(entry.getKey(), word));
			}
		}
		
		return everything;
	}

	private void populateKnowledgeSource(Map<String, Set<KnowledgeSource>> everything, String word, String category, String reference) {
		//Have we seen this word before, create a new set of knowledge sources if not using computeIfAbsent
		Set<KnowledgeSource> sources = everything.computeIfAbsent(word, k -> new HashSet<>());
		sources.add(new KnowledgeSource(category, reference));
	}

	public boolean startsWithCaseInsensitivePrefix(String term) {
		for (String prefix : caseInsensitivePrefix) {
			if (term.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	public boolean isAllNumbersOrSymbols(String term) {
		return term.matches("[^\\p{L}]+");
	}

}
