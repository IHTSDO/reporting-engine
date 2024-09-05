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
import java.util.stream.Collectors;

public class DialectChecker implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DialectChecker.class);

	private List<DialectPair> dialectPairs = null;
	private Set<String> usTerms = new HashSet<>();
	private Set<String> gbTerms = new HashSet<>();
	private static DialectChecker singleton = null;

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
		singleton.init();
		return singleton;
	}

	public int size() throws TermServerScriptException {
		return DialectChecker.create().dialectPairs.size();
	}

	public List<DialectPair> getDialectPairs() throws TermServerScriptException {
		return DialectChecker.create().dialectPairs;
	}

	private void init() throws TermServerScriptException {
		List<String> lines;
		LOGGER.debug("Loading us/gb terms");
		try {
			lines = Files.readLines(new File("resources/us-to-gb-terms-map.txt"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read resources/us-to-gb-terms-map.txt", e);
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

	private boolean containsDialectSpecificTerm(String term, Set<String> dialectSpecificTerms) {
		//Split the term up into a list of lower case words
		String[] words = term.toLowerCase().split(" ");
		for (String word : words) {
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
		//Split the term up into a list of lower case words
		String[] words = term.toLowerCase().split(" ");
		for (String word : words) {
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
		String[] words = term.split(" ");
		boolean replacementMade = false;
		for (int i = 0; i < words.length; i++) {
			for (DialectPair pair : dialectPairs) {
				//The word might end with some punctuation, so split that off before comparing, and
				//add back on afterwards
				String[] wordPunctuation = words[i].split("(?<=\\w)(?=\\W)");
				String wordLower = wordPunctuation[0].toLowerCase();
				String punctuation = wordPunctuation.length == 2 ? wordPunctuation[1] : "";

				if (pair.getTerm(from).equals(wordLower)) {
					String replacement = pair.getTerm(to);
					//Are we adjusting for case?
					if (!wordPunctuation[0].equals(wordLower)) {
						replacement = StringUtils.capitalizeFirstLetter(replacement);
					}
					words[i] = replacement + punctuation;
					replacementMade = true;
					break;
				}
			}
		}
		//If we didn't find a replacement, return the original term.  Otherwise, rejoin the words
		if (replacementMade) {
			return String.join(" ", words);
		} else {
			return term;
		}
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
