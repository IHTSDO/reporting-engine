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
import java.util.stream.Stream;

public class DialectChecker implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DialectChecker.class);
	//Words are runs of letters, so punctuation either side or in between (eg brackets, slashes, hyphens) is kept
	private static final Pattern WORD = Pattern.compile("\\p{L}+");
	private static final String PREFERRED_TERMS_MAP_FILE = "resources/us-to-gb-preferred-terms-map.txt";
	//Pairs where the US term is preferred in GB too, with the GB term only acceptable, eg fetal/foetal
	private static final String ACCEPTABLE_TERMS_MAP_FILE = "resources/us-to-gb-acceptable-terms-map.txt";

	private List<DialectPair> dialectPairs = null;
	private Set<String> usTerms = new HashSet<>();
	private Set<String> gbTerms = new HashSet<>();
	private Set<String> acceptableMapTerms = new HashSet<>();
	private static DialectChecker singleton = null;
	private static DialectChecker singletonIncludingAcceptableTerms = null;

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
		singleton.init(false);
		return singleton;
	}

	/**
	 * As create(), but also loads the acceptable terms map.  Its US terms are preferred in GB too, so aren't US
	 * specific, while its GB terms are GB specific, but only ever acceptable.
	 */
	public static DialectChecker createIncludingAcceptableTerms() throws TermServerScriptException {
		if (singletonIncludingAcceptableTerms != null) {
			return singletonIncludingAcceptableTerms;
		}
		singletonIncludingAcceptableTerms = new DialectChecker();
		singletonIncludingAcceptableTerms.init(true);
		return singletonIncludingAcceptableTerms;
	}

	public int size() {
		return dialectPairs.size();
	}

	public List<DialectPair> getDialectPairs() {
		return dialectPairs;
	}

	private void init(boolean includeAcceptableTerms) throws TermServerScriptException {
		List<DialectPair> pairs = new ArrayList<>(loadPairs(PREFERRED_TERMS_MAP_FILE, false));
		if (includeAcceptableTerms) {
			pairs.addAll(loadPairs(ACCEPTABLE_TERMS_MAP_FILE, true));
		}
		dialectPairs = List.copyOf(pairs);

		usTerms = dialectPairs.stream()
				.filter(pair -> !pair.usPreferredInGb)
				.map(pair -> pair.usTerm)
				.collect(Collectors.toSet());

		gbTerms = dialectPairs.stream()
				.map(pair -> pair.gbTerm)
				.collect(Collectors.toSet());

		acceptableMapTerms = dialectPairs.stream()
				.filter(pair -> pair.usPreferredInGb)
				.flatMap(pair -> Stream.of(pair.usTerm, pair.gbTerm))
				.collect(Collectors.toSet());
	}

	private List<DialectPair> loadPairs(String mapFile, boolean usPreferredInGb) throws TermServerScriptException {
		LOGGER.debug("Loading us/gb terms from {}", mapFile);
		try {
			return Files.readLines(new File(mapFile), StandardCharsets.UTF_8).stream()
					.map(line -> new DialectPair(line, usPreferredInGb))
					.toList();
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + mapFile, e);
		}
	}

	/**
	 * @return true if the word (either spelling) comes from the acceptable terms map, eg fetal or foetal
	 */
	public boolean isAcceptableMapWord(String word) {
		return acceptableMapTerms.contains(word.toLowerCase());
	}

	/**
	 * @return true if the term features a GB word that would normally only be acceptable, eg foetal
	 */
	public boolean containsAcceptableOnlyGBTerm(String term) {
		return getWordsLower(term).stream()
				.anyMatch(word -> dialectPairs.stream().anyMatch(pair -> pair.usPreferredInGb && pair.gbTerm.equals(word)));
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
	 * @param includeUsPreferredInGb whether to convert US words that are preferred in GB too, eg fetal to foetal
	 * @return the word in the other dialect, or the word unchanged if it isn't specific to the "from" dialect
	 */
	private String convertWord(String word, Dialect from, Dialect to, boolean includeUsPreferredInGb) {
		String wordLower = word.toLowerCase();
		for (DialectPair pair : dialectPairs) {
			if (pair.usPreferredInGb && from == Dialect.US && !includeUsPreferredInGb) {
				continue;
			}
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

	/**
	 * @return each GB specific word in the term, in lower case, with its US equivalent
	 */
	public Map<String, String> getGBSpecificWords(String term) {
		Map<String, String> gbToUsWords = new LinkedHashMap<>();
		for (String word : getWordsLower(term)) {
			String usWord = convertWord(word, Dialect.GB, Dialect.US, false);
			if (!usWord.equals(word)) {
				gbToUsWords.put(word, usWord);
			}
		}
		return gbToUsWords;
	}

	public String findFirstUSGBSpecificTerm(Description d) {
		return findFirstUSGBSpecificTerm(d.getTerm());
	}

	public String findFirstUSGBSpecificTerm(String term) {
		for (String word : getWordsLower(term)) {
			for (DialectPair pair : dialectPairs) {
				if (pair.usTerm.equals(word) && !pair.usPreferredInGb) {
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
		return makeDialectSpecific(term, Dialect.GB, Dialect.US, false);
	}

	/**
	 * The GB preferred form: US words that are preferred in GB too (eg fetal) are left as they are
	 */
	public String makeGBSpecific(String term) {
		return makeDialectSpecific(term, Dialect.US, Dialect.GB, false);
	}

	/**
	 * As makeGBSpecific, but also converting US words that are preferred in GB too, eg fetal to foetal, to give
	 * the form that is only acceptable in GB
	 */
	public String makeGBAcceptableForm(String term) {
		return makeDialectSpecific(term, Dialect.US, Dialect.GB, true);
	}

	private String makeDialectSpecific(String term, Dialect from, Dialect to, boolean includeUsPreferredInGb) {
		Matcher matcher = WORD.matcher(term);
		StringBuilder converted = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(converted, Matcher.quoteReplacement(convertWord(matcher.group(), from, to, includeUsPreferredInGb)));
		}
		matcher.appendTail(converted);
		return converted.toString();
	}

	public class DialectPair {
		public final String usTerm;
		public final String gbTerm;
		public final String usTermPadded;
		public final String gbTermPadded;
		//From the acceptable terms map: the US term is preferred in GB too, the GB term only acceptable
		public final boolean usPreferredInGb;
		DialectPair (String line, boolean usPreferredInGb) {
			this.usPreferredInGb = usPreferredInGb;
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
