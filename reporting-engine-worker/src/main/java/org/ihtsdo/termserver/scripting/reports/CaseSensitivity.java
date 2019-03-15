package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * DRUGS-269, SUBST-130, MAINT-77, INFRA-2723, MAINT-345
 * Lists all case sensitive terms that do not have capital letters after the first letter
 * UPDATE: We'll also load in the existing cs_words.txt file instead of hardcoding a list of proper nouns.
 */
public class CaseSensitivity extends TermServerReport implements ReportClass {
	
	List<Concept> targetHierarchies = new ArrayList<>();
	List<Concept> excludeHierarchies = new ArrayList<>();
	Map<String, Description> sourcesOfTruth = new HashMap<>();
	Set<Concept> allExclusions = new HashSet<>();
	Set<String> whiteList = new HashSet<>();  //Note this can be both descriptions and concept SCTIDs
	boolean newlyModifiedContentOnly = true;
	List<String> properNouns = new ArrayList<>();
	Map<String, List<String>> properNounPhrases = new HashMap<>();
	List<String> knownLowerCase = new ArrayList<>();
	Pattern numberLetter = Pattern.compile("\\d[a-z]");
	Pattern singleLetter = Pattern.compile("[^a-zA-Z][a-z][^a-zA-Z]");
	Set<String>wilcardWords = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		TermServerReport.run(CaseSensitivity.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		additionalReportColumns = "FSN, Semtag, Description, isPreferred, CaseSignificance, Issue";
		inputFile = new File("resources/cs_words.txt");
	}
	
	public void postInit() throws TermServerScriptException {
		super.postInit();
		loadCSWords();
		info ("Processing exclusions");
		targetHierarchies.add(ROOT_CONCEPT);
		//targetHierarchies.add(gl.getConcept("771115008"));
		excludeHierarchies.add(SUBSTANCE);
		excludeHierarchies.add(ORGANISM);
		for (Concept excludeThis : excludeHierarchies) {
			excludeThis = gl.getConcept(excludeThis.getConceptId());
			allExclusions.addAll(gl.getDescendantsCache().getDescendents(excludeThis));
		}
		
		//We're making these exclusions because they're a source of truth for CS
		for (Concept c : allExclusions) {
			for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
				if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					String term = d.getTerm();
					if (d.getType().equals(DescriptionType.FSN)) {
						term = SnomedUtils.deconstructFSN(term)[0];
					}
					sourcesOfTruth.put(term, d);
				}
			}
		}
		
		whiteList.add("3722547016");
		whiteList.add("3722542010");
		whiteList.add("3737657014");
	}

	@Override
	public Job getJob() {
		return new Job( new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION),
						"Case Significance",
						"This report validates the case significance of new and modified descriptions.  Note that the Substances and Organism hierarchies are excluded as they are taken to be a 'source of truth'. " +
						"The 'Issues' count here reflects the number of rows in the report.", 
						new JobParameters(new String[] { }));
	}

	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		checkCaseSignificance();
	}

	public void loadCSWords() throws TermServerScriptException {
		print("Loading " + inputFile + "...");
		if (!inputFile.canRead()) {
			throw new TermServerScriptException("Cannot read: " + inputFile);
		}
		List<String> lines;
		try {
			lines = Files.readLines(inputFile, Charsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Failure while reading: " + inputFile, e);
		}
		info ("Complete");
		debug("Processing cs words file");
		for (String line : lines) {
			//Split the line up on tabs
			String[] items = line.split(TAB);
			String phrase = items[0];
			//Does the word contain a capital letter (ie not the same as it's all lower case variant)
			if (!phrase.equals(phrase.toLowerCase())) {
				//Does this word end in a wildcard?
				if (phrase.endsWith("*")) {
					String wildWord = phrase.replaceAll("\\*", "");
					wilcardWords.add(wildWord);
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

	private void checkCaseSignificance() throws TermServerScriptException {
		//Work through all active descriptions of all hierarchies
		for (Concept targetHierarchy : targetHierarchies) {
			List<Concept> hiearchyDescendants = new ArrayList<>(targetHierarchy.getDescendents(NOT_SET));
			//Sorting is doing String creation and manipulation multiple times for each concept
			//We'll let the user do the sorting in the spreadsheet output if they want.
			/*info ("Sorting descendants: " + targetHierarchy);
			Collections.sort(hiearchyDescendants, new Comparator<Concept>() {
				@Override
				public int compare(Concept c1, Concept c2) {
					try {
						String sortOn1 = SnomedUtils.deconstructFSN(c1.getFsn())[1] + c1.getFsn();
						String sortOn2 = SnomedUtils.deconstructFSN(c2.getFsn())[1] + c2.getFsn();
						return sortOn1.compareTo(sortOn2);
					} catch (Exception e) {
						warn(e.toString() + " sorting " + c1 + " and " + c2);
					}
					return c1.getConceptId().compareTo(c2.getConceptId());
				}
			});*/
			
			info ("Checking case significance in target hierarchy: " + targetHierarchy);
			
			int count = 0;
			nextConcept:
			for (Concept c : hiearchyDescendants) {
				if (whiteListedConcepts.contains(c)) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				if (++count %10000 == 0) {
					print (".");
				}
				
				if (c.getConceptId().equals("322280009")) {
//					debug ("Temp - check here");
				}
				if (allExclusions.contains(c) || whiteList.contains(c.getId())) {
					continue;
				}
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					incrementSummaryInformation("Descriptions checked");
					if (whiteList.contains(d.getDescriptionId())) {
						continue;
					}
					if (!newlyModifiedContentOnly || !d.isReleased()) {
						String term = d.getTerm().replaceAll("\\-", " ");
						String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
						String firstLetter = term.substring(0,1);
						String chopped = term.substring(1);
						String preferred = d.isPreferred()?"Y":"N";
						
						//Text Definitions must be CS
						if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
							if (!caseSig.equals(CS)) {
								report(c, d, preferred, caseSig, "Text Definitions must be CS");
								incrementSummaryInformation(ISSUE_COUNT);
							}
						} else if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
							//Lower case first letters must be entire term case sensitive
							report(c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
							incrementSummaryInformation(ISSUE_COUNT);
							continue nextConcept;
						} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
							if (chopped.equals(chopped.toLowerCase()) && 
									!singleLetterCombo(term) && 
									!startsWithProperNounPhrase(term) &&
									!containsKnownLowerCaseWord(term)) {
								if (caseSig.equals(CS) && startsWithSingleLetter(d.getTerm())){
									//Probably OK
								} else {
									report(c, d, preferred, caseSig, "Case sensitive term does not have capital after first letter");
									incrementSummaryInformation(ISSUE_COUNT);
									continue nextConcept;
								}
							}
						} else {
							//For case insensitive terms, we're on the look out for capital letters after the first letter
							if (!chopped.equals(chopped.toLowerCase())) {
								report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
								incrementSummaryInformation(ISSUE_COUNT);
								continue nextConcept;
							}
							
							//Or if one of our sources of truth?
							String firstWord = d.getTerm().split(" ")[0];
							if (sourcesOfTruth.containsKey(firstWord)) {
								report (c, d, preferred, caseSig, "Case insensitive term should be CS as per " + sourcesOfTruth.get(firstWord));
								incrementSummaryInformation(ISSUE_COUNT);
								continue nextConcept;
							}
						}
					}
				}
			}
			info ("Completed hierarchy: " + targetHierarchy);
			
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
		for (String wildword : wilcardWords) {
			if (firstWord.startsWith(wildword)) {
				return true;
			}
		}
		
		//Is the first word or the phrase one of our sources of truth?
		if (sourcesOfTruth.containsKey(firstWord) || sourcesOfTruth.containsKey(term)) {
			return true;
		} 
		
		//Work the number of words up progressively to see if we get a match 
		//eg first two words in "Influenza virus vaccine-containing product in nasal dose form" is an Organism
		String progressive = firstWord;
		for (int i=1; i<words.length; i++) {
			progressive += " " + words[i];
			if (sourcesOfTruth.containsKey(progressive)) {
				return true;
			}
		}
		
		return false;
	}

	private boolean singleLetterCombo(String term) {
		//Do we have a letter following a number - optionally with a dash?
		term = term.replaceAll("-", "");
		Matcher matcher = numberLetter.matcher(term);
		if (matcher.find()) {
			return true;
		}
		
		//A letter on it's own will often be lower case eg 3715305012 [768869001] US: P, GB: P: Interferon alfa-n3-containing product [cI]
		matcher = singleLetter.matcher(term);
		if (matcher.find()) {
			return true;
		}
		return false;
	}

	public boolean singleCapital(String term) {
		if (Character.isUpperCase(term.charAt(0))) {
			if (term.length() == 1) {
				return true;
			} else if (!Character.isLetter(term.charAt(1))) {
				return true;
			}
		}
		return false;
	}

}
