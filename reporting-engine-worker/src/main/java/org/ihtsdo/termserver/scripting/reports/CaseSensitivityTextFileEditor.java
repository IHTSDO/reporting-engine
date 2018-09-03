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
 * SUBST-130
 * Class to work with the cs_words.txt file
 * 
 * TODO: remove any greek letter derived words (SUBST-288)
 */
public class CaseSensitivityTextFileEditor extends TermServerReport{
	
	List<Concept> targetHierarchies = new ArrayList<>();
	List<Concept> excludeHierarchies = new ArrayList<>();
	Set<Concept> allExclusions = new HashSet<>();
	boolean newlyModifiedContentOnly = false;
	List<String> properNouns = new ArrayList<>();
	Map<String, List<String>> properNounPhrases = new HashMap<>();
	List<String> knownLowerCase = new ArrayList<>();
	Pattern numberLetter = Pattern.compile("\\d[a-z]");
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSensitivityTextFileEditor report = new CaseSensitivityTextFileEditor();
		try {
			report.additionalReportColumns = "SemTag, Desc, Term, CS, LogicRuleOK, In CS_Words.txt, CS Issue";
			report.init(args);
			report.loadCSWords();
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			info ("Producing cs words report...");
			report.checkCaseSignificance();
		} finally {
			report.finish();
		}
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		//targetHierarchies.add(PHARM_BIO_PRODUCT);
		targetHierarchies.add(SUBSTANCE);
		//targetHierarchies.add(ROOT_CONCEPT);
		//targetHierarchies.add(MEDICINAL_PRODUCT);
		
		//excludeHierarchies.add(SUBSTANCE);
		//excludeHierarchies.add(ORGANISM);
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
			List<Concept> descendants = new ArrayList<>(targetHierarchy.getDescendents(NOT_SET));
			descendants.sort(Comparator.comparing(Concept::getFsn, String.CASE_INSENSITIVE_ORDER));
			for (Concept c : descendants) {
				if (allExclusions.contains(c)) {
					continue;
				}
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean reported = false;
					String term = d.getTerm();
					String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
					
					if (d.getTerm().startsWith("1,1,-Dichloropropane")) {
						//debug ("Check here");
					}
					
					if (d.getType().equals(DescriptionType.FSN)) {
						term = SnomedUtils.deconstructFSN(term)[0];
					}
					//Nicki is just interested in single word substances just now
					String[] words = term.split(" ");
					if (words.length > 1) {
						continue;
					}
					String inFile = properNouns.contains(term) ? "Y":"N";
					
					if (!newlyModifiedContentOnly || !d.isReleased()) {
						String firstLetter = d.getTerm().substring(0,1);
						String chopped = d.getTerm().substring(1);
						//Lower case first letters must be entire term case sensitive
						//But if they are case sensititive, there can be nothing else wrong with them
						if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase())) {
							if (caseSig.equals(CS)) {
								//All Good!
							} else {
								report (c, d, term, caseSig, "-", inFile, "Terms starting with lower case letter must be CS");
								reported = true;
								incrementSummaryInformation("issues");
							}
						} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
							if (chopped.equals(chopped.toLowerCase()) && !logicRuleOK(caseSig, term)) {
								report (c, d, term, caseSig, "N", inFile,"Case sensitive term does not have capital after first letter");
								reported = true;
								incrementSummaryInformation("issues");
							}
						} else {
							//For case insensitive terms, we're on the look out for capital letters after the first letter
							if (!chopped.equals(chopped.toLowerCase())) {
								report (c, d, term, caseSig, "-", inFile,"Case insensitive term has a capital after first letter");
								reported = true;
								incrementSummaryInformation("issues");
							}
						}
					}
					if (!reported) {
						report (c, d, term, caseSig, logicRuleOK(caseSig, term)?"Y":"N", inFile, "No issue reported");
					}
				}
			}
		}
	}
	
	private boolean logicRuleOK(String indicator, String term) {
		return  letterFollowsNumber(term) ||
				startsWithProperNounPhrase(term) ||
				containsKnownLowerCaseWord(term) ||
				(indicator.equals(CS) && startsWithSingleLetter(term));
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
}
