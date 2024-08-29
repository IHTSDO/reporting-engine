package org.ihtsdo.termserver.scripting.util;

import com.google.common.io.Files;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DialectChecker implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DialectChecker.class);

	private List<DialectPair> dialectPairs = null;
	private static DialectChecker singleton = null;

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
	}

	public boolean containsUSGBSpecificTerm(Description d) {
		//Split the term up into a list of lower case words
		String[] words = d.getTerm().toLowerCase().split(" ");
		for (String word : words) {
			for (DialectPair pair : dialectPairs) {
				if (pair.usTerm.equals(word) || pair.gbTerm.equals(word)) {
					return true;
				}
			}
		}
		return false;
	}

	public String findFirstUSGBSpecificTerm(Description d) {
		//Split the term up into a list of lower case words
		String[] words = d.getTerm().toLowerCase().split(" ");
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

	public class DialectPair {
		public final String usTerm;
		public final String gbTerm;
		DialectPair (String line) {
			String[] pair = line.split(TAB);
			//Wrap in spaces to ensure whole word matching
			usTerm = " " + pair[0] + " ";
			gbTerm = " " + pair[1] + " ";
		}
	}
}
