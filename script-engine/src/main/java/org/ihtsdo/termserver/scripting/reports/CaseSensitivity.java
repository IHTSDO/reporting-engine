package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * DRUGS-269, SUBST-130, MAINT-77, INFRA-2723, MAINT-345
 * Lists all case sensitive terms that do not have capital letters after the first letter
 * UPDATE: We'll also load in the existing cs_words.txt file instead of hardcoding a list of proper nouns.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSensitivity extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSensitivity.class);

	private static String INCLUDE_SUB_ORG = "Include Substances and Organisms";
	private static String RECENT_CHANGES_ONLY = "Recent Changes Only";

	private List<Concept> targetHierarchies = new ArrayList<>();
	private List<Concept> excludeHierarchies = new ArrayList<>();
	private Map<String, Description> sourcesOfTruth = new HashMap<>();
	private Set<Concept> allExclusions = new HashSet<>();
	private Set<String> whiteList = new HashSet<>();  //Note this can be both descriptions and concept SCTIDs
	private boolean includeSubOrg = false;
	private boolean recentChangesOnly = true;
	private List<String> properNouns = new ArrayList<>();
	private Map<String, List<String>> properNounPhrases = new HashMap<>();
	private List<String> knownLowerCase = new ArrayList<>();
	private Pattern numberLetter = Pattern.compile("\\d[a-z]");
	private Pattern singleLetter = Pattern.compile("[^a-zA-Z][a-z][^a-zA-Z]");
	private Set<String>wilcardWords = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, Object> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		params.put(RECENT_CHANGES_ONLY, "N");
		TermServerReport.run(CaseSensitivity.class, params, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		additionalReportColumns = "FSN, Semtag, Description, isPreferred, CaseSignificance, Issue";
		inputFiles.add(0, new File("resources/cs_words.tsv"));
	}
	
	public void postInit() throws TermServerScriptException {
		super.postInit();
		loadCSWords();
		LOGGER.info ("Processing exclusions");
		targetHierarchies.add(ROOT_CONCEPT);
		recentChangesOnly = getJob().getParameters().getMandatoryBoolean(RECENT_CHANGES_ONLY);
		includeSubOrg = getJob().getParameters().getMandatoryBoolean(INCLUDE_SUB_ORG);
		if (!includeSubOrg) {
			excludeHierarchies.add(SUBSTANCE);
			excludeHierarchies.add(ORGANISM);
		}

		for (Concept excludeThis : excludeHierarchies) {
			excludeThis = gl.getConcept(excludeThis.getConceptId());
			allExclusions.addAll(gl.getDescendantsCache().getDescendants(excludeThis));
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
		JobParameters params = new JobParameters()
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(INCLUDE_SUB_ORG).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(RECENT_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Case Significance")
				.withDescription("This report validates the case significance of new and modified descriptions.  Note that the Substances and Organism hierarchies are normally excluded as they are taken to be a 'source of truth' and since most Organisms start with proper nouns we see a lot of false positives, but this setting can be overridden. " +
									"The 'Issues' count here reflects the number of rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		checkCaseSignificance();
	}

	public void loadCSWords() throws TermServerScriptException {
		print("Loading " + getInputFile() + "...");
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException("Cannot read: " + getInputFile());
		}
		List<String> lines;
		try {
			lines = Files.readLines(getInputFile(), Charsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Failure while reading: " + getInputFile(), e);
		}
		LOGGER.info ("Complete");
		LOGGER.debug("Processing cs words file");
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
			List<Concept> hiearchyDescendants = new ArrayList<>(targetHierarchy.getDescendants(NOT_SET));
			LOGGER.info ("Checking case significance in target hierarchy: " + targetHierarchy);
			
			int count = 0;
			nextConcept:
			for (Concept c : hiearchyDescendants) {
				if (whiteListedConceptIds.contains(c.getId())) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				if (++count %10000 == 0) {
					print (".");
				}
				
/*				if (c.getConceptId().equals("688271000119100")) {
					LOGGER.debug ("Temp - check here");
				}*/
				if (allExclusions.contains(c) || whiteList.contains(c.getId())) {
					continue;
				}
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					//Are we checking only unpromoted changes?
					if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(d)) {
						continue;
					}
					incrementSummaryInformation("Descriptions checked");
					if (whiteList.contains(d.getDescriptionId())) {
						continue;
					}
					if (!recentChangesOnly || !d.isReleased()) {
						String term = d.getTerm().replaceAll("\\-", " ");
						String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
						String firstLetter = term.substring(0,1);
						String secondLetter = term.substring(1,2);
						String chopped = term.substring(1);
						String preferred = d.isPreferred()?"Y":"N";
						
						//Text Definitions must be CS
						if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
							if (!caseSig.equals(CS)) {
								report(c, d, preferred, caseSig, "Text Definitions must be CS");
								countIssue(c);
							}
						} else if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
							//Lower case first letters must be entire term case sensitive
							report(c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
							countIssue(c);
							continue nextConcept;
						} else if ((Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toUpperCase()))
								&& (Character.isLetter(secondLetter.charAt(0)) && secondLetter.equals(secondLetter.toUpperCase()))
								&& !caseSig.equals(CS)) {
							//Terms starting with acronyms should be entire term case sensitive
							report(c, d, preferred, caseSig, "Terms starting with acronyms must be CS");
							countIssue(c);
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
									countIssue(c);
									continue nextConcept;
								}
							}
						} else {
							//For case insensitive terms, we're on the look out for capital letters after the first letter
							if (!chopped.equals(chopped.toLowerCase())) {
								report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
								countIssue(c);
								continue nextConcept;
							}
							
							//Or if one of our sources of truth?
							String firstWord = d.getTerm().split(" ")[0];
							if (sourcesOfTruth.containsKey(firstWord)) {
								report (c, d, preferred, caseSig, "Case insensitive term should be CS as per " + sourcesOfTruth.get(firstWord));
								countIssue(c);
								continue nextConcept;
							}
						}
					}
				}
			}
			print ("\n\n");
			LOGGER.info ("Completed hierarchy: " + targetHierarchy);
			
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
