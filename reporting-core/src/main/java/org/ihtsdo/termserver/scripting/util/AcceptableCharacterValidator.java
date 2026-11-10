package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Character validation support for SNOMED CT descriptions.
 */
public final class AcceptableCharacterValidator {

	private static final String ALL_EXTENSIONS = "ALL";
	private static final String INTERNATIONAL = "INT";
	private static final String EXTENSION_PREFIX = WhitelistHelper.DEFAULT_CODE_SYSTEM + "-";

	private final String extensionCode;

	//Rules specific to this extension are checked first, so they take precedence over rules for ALL
	private final List<UnicodeRangeRule> extensionRules = new ArrayList<>();
	private final List<UnicodeRangeRule> generalRules = new ArrayList<>();

	public enum Action {
		ALLOW,
		WARNING,
		DISALLOW
	}

	private AcceptableCharacterValidator(String extensionCode) {
		//Don't instantiate directly, call getInstance()
		this.extensionCode = extensionCode;
	}

	/**
	 * @param codeSystem eg SNOMEDCT for the International Edition, or SNOMEDCT-CH for the Swiss Extension
	 */
	public static AcceptableCharacterValidator getInstance(String codeSystem) throws TermServerScriptException {
		AcceptableCharacterValidator acv = new AcceptableCharacterValidator(deriveExtensionCode(codeSystem));
		try {
			File acceptableCharsFile = new File("resources/acceptable_characters.tsv");
			acv.loadPolicy(acceptableCharsFile.toPath());
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to load acceptable character policy", e);
		}
		return acv;
	}

	/**
	 * @return the 'super-short' identifier used in the policy file, eg SNOMEDCT gives INT, SNOMEDCT-CH gives CH
	 */
	static String deriveExtensionCode(String codeSystem) {
		if (codeSystem == null || codeSystem.equals(WhitelistHelper.DEFAULT_CODE_SYSTEM)) {
			return INTERNATIONAL;
		}
		return codeSystem.startsWith(EXTENSION_PREFIX) ? codeSystem.substring(EXTENSION_PREFIX.length()) : codeSystem;
	}

	//Load the Unicode character policy from a tab-delimited file.
	private void loadPolicy(Path policyFile) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(policyFile, StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#")) {
					loadRule(line, lineNumber);
				}
			}
		}
	}

	//Columns are: code point or range, action, extensions, comment.
	//The older format has no extensions column, so its rules apply to all extensions.
	private void loadRule(String line, int lineNumber) {
		String[] columns = line.split("\t", -1);
		if (columns.length < 2) {
			throw new IllegalArgumentException(
					"Invalid policy line " + lineNumber + ": " + line
			);
		}

		String rangeSpec = columns[0].trim();
		Action action = Action.valueOf(columns[1].trim());
		String extensions = ALL_EXTENSIONS;
		String comment = null;
		if (columns.length >= 3 && columns[2].trim().startsWith("#")) {
			comment = columns[2].trim();
		} else if (columns.length >= 3) {
			extensions = columns[2].trim();
			comment = columns.length >= 4 ? columns[3].trim() : null;
		}

		int start;
		int end;

		if (rangeSpec.contains("–")) { // EN DASH as range separator
			String[] parts = rangeSpec.split("–");
			start = parseCodePoint(parts[0]);
			end = parseCodePoint(parts[1]);
		} else {
			start = parseCodePoint(rangeSpec);
			end = start;
		}

		UnicodeRangeRule rule = new UnicodeRangeRule(start, end, action, comment);
		List<String> appliesTo = Arrays.stream(extensions.split(",")).map(String::trim).toList();
		if (appliesTo.contains(ALL_EXTENSIONS)) {
			generalRules.add(rule);
		} else if (appliesTo.contains(extensionCode)) {
			extensionRules.add(rule);
		}
	}

	private UnicodeRangeRule findRule(int codePoint) {
		for (UnicodeRangeRule rule : extensionRules) {
			if (rule.matches(codePoint)) {
				return rule;
			}
		}
		for (UnicodeRangeRule rule : generalRules) {
			if (rule.matches(codePoint)) {
				return rule;
			}
		}
		return null;
	}

	private int parseCodePoint(String s) {
		s = s.trim();
		if (!s.startsWith("U+")) {
			throw new IllegalArgumentException("Invalid Unicode code point: " + s);
		}
		return Integer.parseInt(s.substring(2), 16);
	}

	//Validate a string against the supplied policy rules.
	//Returns all WARN and DISALLOW findings with positions.
	public List<ValidationIssue> validateString (String input) {
		List<ValidationIssue> issues = new ArrayList<>();

		int charIndex = 0;
		int codePointIndex = 0;

		while (charIndex < input.length()) {
			int codePoint = input.codePointAt(charIndex);

			UnicodeRangeRule matchedRule = findRule(codePoint);

			if (matchedRule == null) {
				issues.add(new ValidationIssue(
						codePoint,
						charIndex,
						codePointIndex,
						Action.DISALLOW,
						"No matching rule"
				));
			} else if (matchedRule.action != Action.ALLOW) {
				issues.add(new ValidationIssue(
						codePoint,
						charIndex,
						codePointIndex,
						matchedRule.action,
						matchedRule.comment
				));
			}

			charIndex += Character.charCount(codePoint);
			codePointIndex++;
		}

		return issues;
	}


	//One Unicode range rule from the configuration file.
	public static final class UnicodeRangeRule {
		public final int startCodePoint;
		public final int endCodePoint;
		public final Action action;
		public final String comment;

		public UnicodeRangeRule(int startCodePoint, int endCodePoint, Action action, String comment) {
			this.startCodePoint = startCodePoint;
			this.endCodePoint = endCodePoint;
			this.action = action;
			this.comment = comment;
		}

		public boolean matches(int codePoint) {
			return codePoint >= startCodePoint && codePoint <= endCodePoint;
		}
	}

	//Result container for validation.
	public static final class ValidationIssue {
		public final int codePoint;
		public final String character;
		public final String unicodeName;
		public final int charIndex;       // UTF-16 index
		public final int codePointIndex;  // Unicode code point index
		public final Action action;
		public final String comment;

		public ValidationIssue(
				int codePoint,
				int charIndex,
				int codePointIndex,
				Action action,
				String comment
		) {
			this.codePoint = codePoint;
			this.character = new String(Character.toChars(codePoint));
			this.unicodeName = Character.getName(codePoint);
			this.charIndex = charIndex;
			this.codePointIndex = codePointIndex;
			this.action = action;
			this.comment = comment;
		}

		@Override
		public String toString() {
			String namePart = unicodeName != null ? unicodeName : "UNKNOWN NAME";
			return String.format(
					"%s U+%04X (%s) '%s' at position %d - %s",
					comment,
					codePoint,
					namePart,
					character,
					charIndex,
					action.name().toLowerCase()
			);
		}
	}

}

