package org.ihtsdo.termserver.scripting.util;

import com.google.common.io.Files;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DialectChecker implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DialectChecker.class);
	//Words are runs of letters, so punctuation either side or in between (eg brackets, slashes, hyphens) is kept
	private static final Pattern WORD = Pattern.compile("\\p{L}+");
	private static final String TERMS_MAP_FILE = "resources/us-to-gb-terms-map.txt";
	//Pairs where the US term is preferred, with the GB term also acceptable
	private static final String SYNONYMS_MAP_FILE = "resources/us-to-gb-synonyms-map.txt";

	private List<DialectPair> dialectPairs = null;
	private Set<String> usTerms = new HashSet<>();
	private Set<String> gbTerms = new HashSet<>();
	private static DialectChecker singleton = null;
	private static DialectChecker singletonIncludingSynonyms = null;

	enum Dialect {
		US,
		GB
	}

	private DialectChecker() {
		//Singleton, obtain via "create"
	}

	public static DialectChecker create() throws TermServerScriptException {
		if (singleton != null) {
			return singleton;
		}
		singleton = new DialectChecker();
		singleton.init(TERMS_MAP_FILE);
		return singleton;
	}

	/**
	 * As create(), but also treats the synonyms map's pairs as dialect specific
	 */
	public static DialectChecker createIncludingSynonyms() throws TermServerScriptException {
		if (singletonIncludingSynonyms != null) {
			return singletonIncludingSynonyms;
		}
		singletonIncludingSynonyms = new DialectChecker();
		singletonIncludingSynonyms.init(TERMS_MAP_FILE, SYNONYMS_MAP_FILE);
		return singletonIncludingSynonyms;
	}

	public int size() {
		return dialectPairs.size();
	}

	public List<DialectPair> getDialectPairs() {
		return dialectPairs;
	}

	private void init(String... mapFiles) throws TermServerScriptException {
		List<String> lines = new ArrayList<>();
		for (String mapFile : mapFiles) {
			LOGGER.debug("Loading us/gb terms from {}", mapFile);
			try {
				lines.addAll(Files.readLines(new File(mapFile), StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new TermServerScriptException("Unable to read " + mapFile, e);
			}
		}
		dialectPairs = lines.stream()
				.map(DialectPair::new)
				.toList();

		usTerms = dialectPairs.stream()
				.map(pair -> pair.usTerm)
				.collect(Collectors.toSet());

		gbTerms = dialectPairs.stream()
				.map(pair -> pair.gbTerm)
				.collect(Collectors.toSet());
	}

	public boolean containsUSGBSpecificTerm(String term) {
		return containsUSSpecificTerm(term) || containsGBSpecificTerm(term);
	}

	public boolean containsUSSpecificTerm(String term) {
		return containsDialectSpecificTerm(term, usTerms);
	}

	public boolean containsGBSpecificTerm(String term) {
		return containsDialectSpecificTerm(term, gbTerms);
	}

	/**
	 * @return the word in the other dialect, or the word unchanged if it isn't specific to the "from" dialect
	 */
	private String convertWord(String word, Dialect from, Dialect to) {
		String wordLower = word.toLowerCase();
		for (DialectPair pair : dialectPairs) {
			if (pair.getTerm(from).equals(wordLower)) {
				String replacement = pair.getTerm(to);
				//Are we adjusting for case?
				if (!word.equals(wordLower)) {
					replacement = StringUtils.capitalizeFirstLetter(replacement);
				}
				return replacement;
			}
		}
		return word;
	}

	/**
	 * The term's words in lower case, without any punctuation, eg brackets, commas or slashes
	 */
	private List<String> getWordsLower(String term) {
		List<String> words = new ArrayList<>();
		Matcher matcher = WORD.matcher(term.toLowerCase());
		while (matcher.find()) {
			words.add(matcher.group());
		}
		return words;
	}

	private boolean containsDialectSpecificTerm(String term, Set<String> dialectSpecificTerms) {
		for (String word : getWordsLower(term)) {
			if (dialectSpecificTerms.contains(word)) {
				return true;
			}
		}
		return false;
	}

	public String findFirstUSGBSpecificTerm(Description d) {
		return findFirstUSGBSpecificTerm(d.getTerm());
	}

	public String findFirstUSGBSpecificTerm(String term) {
		for (String word : getWordsLower(term)) {
			for (DialectPair pair : dialectPairs) {
				if (pair.usTerm.equals(word)) {
					return word;
				}
				if (pair.gbTerm.equals(word)) {
					return word;
				}
			}
		}
		return null;
	}

	public String makeUSSpecific(String term) {
		return makeDialectSpecific(term, Dialect.GB, Dialect.US);
	}

	public String makeGBSpecific(String term) {
		return makeDialectSpecific(term, Dialect.US, Dialect.GB);
	}

	private String makeDialectSpecific(String term, Dialect from, Dialect to) {
		Matcher matcher = WORD.matcher(term);
		StringBuilder converted = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(converted, Matcher.quoteReplacement(convertWord(matcher.group(), from, to)));
		}
		matcher.appendTail(converted);
		return converted.toString();
	}

	public class DialectPair {
		public final String usTerm;
		public final String gbTerm;
		public final String usTermPadded;
		public final String gbTermPadded;
		DialectPair (String line) {
			String[] pair = line.split(TAB);
			usTerm = pair[0];
			gbTerm = pair[1];
			//Wrap in spaces to ensure whole word matching
			usTermPadded = " " + usTerm + " ";
			gbTermPadded = " " + gbTerm + " ";
		}

		String getTerm(Dialect dialect) {
			return dialect == Dialect.US ? usTerm : gbTerm;
		}

		String getTermPadded(Dialect dialect) {
			return dialect == Dialect.US ? usTermPadded : gbTermPadded;
		}
	}
}
