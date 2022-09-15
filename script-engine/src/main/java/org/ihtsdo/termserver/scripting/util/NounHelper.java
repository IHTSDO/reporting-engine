package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.snomed.otf.script.Script;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class NounHelper implements RF2Constants {
	
	private static NounHelper singleton = null;

	public List<String> properNouns = new ArrayList<>();
	public Map<String, List<String>> properNounPhrases = new HashMap<>();
	public List<String> knownLowerCase = new ArrayList<>();
	
	public static String[] greekLettersUpper = new String[] { "Alpha", "Beta", "Delta", "Gamma", "Epsilon", "Tau" };
	public static String[] greekLettersLower = new String[] { "alpha", "beta", "delta", "gamma", "epsilon", "tau" };
	
	private NounHelper() {
	}
	
	public static NounHelper instance() throws IOException, TermServerScriptException {
		if (singleton == null) {
			singleton = new NounHelper();
			singleton.loadCSWords(new File("resources/cs_words.tsv"));
		}
		return singleton;
	}
	
	public void loadCSWords(File csFile) throws IOException, TermServerScriptException {
		Script.info("Loading " + csFile);
		if (!csFile.canRead()) {
			throw new TermServerScriptException("Cannot read: " + csFile);
		}
		List<String> lines = Files.readLines(csFile, Charsets.UTF_8);
		for (String line : lines) {
			if (line.startsWith("milliunit/")) {
				//throw new IllegalArgumentException("Check here");
				Script.info("Check cs_words: " + line);
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

	public boolean isProperNoun(String word) {
		return properNouns.contains(word);
	}
	
	public boolean startsWithProperNoun(String term) {
		String firstWord =term.split(" ")[0];
		return isProperNoun(firstWord);
	}
	
	public boolean startsWithProperNounPhrase(String firstWord, String term) {
		//Do we have any phrases that start with this word
		if (properNounPhrases.containsKey(firstWord)) {
			for (String phrase : properNounPhrases.get(firstWord)) {
				if (term.startsWith(phrase)) {
					return true;
				}
			}
		}
		return false;
	}
}
