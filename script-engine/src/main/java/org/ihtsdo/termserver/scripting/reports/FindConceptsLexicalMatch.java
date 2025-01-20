package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.Sets;

/**
 * SUBST-322 Report to find substances that match the entries in a file
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindConceptsLexicalMatch extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(FindConceptsLexicalMatch.class);

	//Map of first words to terms to descriptions.  Everything stored lower case.
	Map<String, Map<String, Description>> termsByFirstWord = new HashMap<>();
	
	//Map of words to descriptions
	Map<String, Set<Concept>> conceptsUsingWord = new HashMap<>();

	//Stop words - common words to ignore when matching
	String[] stopWords = new String[] { "'s", " of ", " and ", " with ", " as ", "\\(as " };
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, SUBSTANCE.toString());
		TermServerScript.run(FindConceptsLexicalMatch.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		additionalReportColumns = "Search Term, Source, ExactMatch, ExactUnorderedMatch, MatchedWith, BestEffort";
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(SUBSTANCE)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Find concepts in list")
				.withDescription("This report lists all concepts that match the lexical terms specified in some file.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void postInit() throws TermServerScriptException {
		//Create various maps of terms
		LOGGER.info("Mapping current descriptions");
		for (Concept c : subHierarchy.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				String term = removeStopWords(d.getTerm());;
				String[] words = term.split(SPACE);
				
				//Have we see this first word before
				Map<String, Description> termMap = termsByFirstWord.get(words[0]);
				if (termMap == null) {
					termMap = new HashMap<>();
					termsByFirstWord.put(words[0], termMap);
				}
				if (termMap.containsKey(term)) {
					//What have we seen before?  If it's the same concept, don't worry
					Description existingDesc = termMap.get(term);
					if (!existingDesc.getConceptId().equals(d.getConceptId())) {
						LOGGER.warn("Duplicate term between " + existingDesc + " and " + d);
					}
				}
				termMap.put(term, d);
				
				//Populate the map of all words used
				for (String word : words) {
					Set<Concept> thisWordConcepts = conceptsUsingWord.get(word);
					if (thisWordConcepts == null) {
						thisWordConcepts = new HashSet<>();
						conceptsUsingWord.put(word, thisWordConcepts);
					}
					thisWordConcepts.add(c);
				}
			}
		}
		LOGGER.info("Description map complete");
		super.postInit();
	}
	
	private String removeStopWords(String term) {
		String cleanTerm = term.toLowerCase();
		//We'll also replace dashes and brackets with spaces
		cleanTerm = cleanTerm.replaceAll("-", " ");
		cleanTerm = cleanTerm.replaceAll("\\(", " ");
		cleanTerm = cleanTerm.replaceAll("\\)", " ");
		
		//If we had two of those in a row, collapse to a single space
		cleanTerm = cleanTerm.replaceAll("  "," ");
		
		for (String stopWord : stopWords) {
			cleanTerm = cleanTerm.replaceAll(stopWord, "");
		}
		return cleanTerm.trim();
	}

	public void runJob() throws TermServerScriptException {
		//Work through the file and attempt to find a match for each term
		try {
			LineIterator it = FileUtils.lineIterator(getInputFile(), "UTF-8");
			try {
				while (it.hasNext()) {
					matchTerm(it.nextLine());
				}
			} finally {
				it.close();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Error reading from: " + getInputFile(), e);
		}
	}

	private void matchTerm(String line) throws TermServerScriptException {
		
		boolean matchFound = false;
		String[] items = line.split(TAB);
		String term = removeStopWords(items[0]);
		String[] words = term.split(SPACE);
		
		//Firstly, can we get an exact match?
		Map<String, Description> termMap = termsByFirstWord.get(words[0]);
		if (termMap!= null && termMap.containsKey(term)) {
			Description d = termMap.get(term);
			Concept c = gl.getConcept(d.getConceptId());
			report(0, null, items[0], items[1], c, null, d);
			matchFound = true;
			LOGGER.info (line + " - exact match");
			incrementSummaryInformation("Exact Match");
		}
		
		//Next thing to try is any word order - only relevant if term has more than one word
		if (!matchFound && words.length > 1 && words.length <= 8) {
			matchFound = matchAnyWordOrder(items, words);
			if (matchFound) {
				incrementSummaryInformation("Exact out-of-order Match");
			}
		}
		
		if (!matchFound && words.length > 1 ) {
			matchFound = bestEffortMatch(items, words);
			if (matchFound) {
				incrementSummaryInformation("Best efforts match");
			}
		}
		
		if (!matchFound) {
			report(0, null, items[0], items[1], null, null, null);
			LOGGER.info (line + " - no match");
			incrementSummaryInformation("No Match");
		}
	}

	private boolean matchAnyWordOrder(String[] source, String[] words) throws TermServerScriptException {
		boolean matchFound = false;
		List<List<String>> wordPermutations = new ArrayList<>();
		List<String> wordsRemaining = Arrays.asList(words);
		permutations(wordPermutations,wordsRemaining, new ArrayList<>());
		for (List<String> thisPermutation : wordPermutations) {
			Map<String, Description> termMap = termsByFirstWord.get(thisPermutation.get(0));
			String term = StringUtils.join(thisPermutation, " ");
			if (termMap!= null && termMap.containsKey(term)) {
				Description d = termMap.get(term);
				Concept c = gl.getConcept(d.getConceptId());
				LOGGER.info (source[0] + " " + source[1] + " - Exact out-of-order Match: " + d);
				report(0, null, source[0], source[1], null, c, d);
				matchFound = true;
			}
		}
		return matchFound;
	}
	
	
	private boolean bestEffortMatch(String[] source, String[] words) throws TermServerScriptException {
		boolean matchFound = false;
		List<Combination> wordCombinations = sortedCombinations(new HashSet<String>(Arrays.asList(words)));
		if (wordCombinations.size() > 1000) {
			LOGGER.debug("Getting intractable here! {}", source[0]);
		}
		
		nextCombination:
		for (Combination thisCombination : wordCombinations) {
			//For each word, get the set of concepts that use that word, 
			//until we fail to find 100% overlap
			Set<Concept> conceptsMatching = null;
			for (String thisWord : thisCombination.words) {
				if (conceptsMatching == null) {
					if (!conceptsUsingWord.containsKey(thisWord)) {
						continue nextCombination;
					}
					conceptsMatching = new HashSet<>(conceptsUsingWord.get(thisWord));
				} else {
					Set<Concept> theseMatches = conceptsUsingWord.get(thisWord);
					if (theseMatches == null || theseMatches.isEmpty()) {
						continue nextCombination;
					}
					//Make sure the concepts we matched have been matched by all previous words
					conceptsMatching.retainAll(theseMatches);
					if (conceptsMatching.isEmpty()) {
						continue nextCombination;
					}
				}
			}
			String matchingWords = String.join(" ", thisCombination.words);
			String matchingConcepts = conceptsMatching.stream()
					.map(Concept::toString)
					.limit(5)
					.collect(Collectors.joining(",\n"));
			LOGGER.info (source[0] + " " + source[1] + " - best effort match: " + matchingConcepts);
			report(0, null, source[0], source[1], null, null, matchingWords, matchingConcepts);
			matchFound = true;
			break;
		}
		return matchFound;
	}

	private List<Combination> sortedCombinations(HashSet<String> words) {
		List<Combination> combinations = new ArrayList<>();
		
		for (Set<String> thisCombinationSet : Sets.powerSet(words)) {
			if (!thisCombinationSet.isEmpty()) {
				combinations.add(new Combination(thisCombinationSet));
			}
		}
		
		//Now sort so that we check the most letter combination first
		Collections.sort(combinations, new Comparator<Combination>() {
			@Override
			public int compare(Combination c1, Combination c2) {
				return c2.letterCount.compareTo(c1.letterCount);
			}
		});
		return combinations;
	}

	/*
	 * Great piece of code though I say so myself, but once you hit 10 words it 
	 * starts to get intractable at 3.7 million.  Add a single word and you jump 
	 * to 39 million. 8 items = 40K permutations, our current practical limit.
	 */
	private void permutations(List <List<String>> wordPermutations, final List<String> wordsRemaining, final List<String> currentWords) { 
		if (wordsRemaining.isEmpty()) {
			wordPermutations.add(currentWords);
		} else {
			for (int i = 0; i < wordsRemaining.size(); i++) {
				List<String> newWords = new ArrayList<>(currentWords);
				List<String> newWordsRemaining = new ArrayList<>(wordsRemaining);
				newWordsRemaining.remove(i);
				newWords.add(wordsRemaining.get(i));
				permutations(wordPermutations, newWordsRemaining, newWords);
			}
		}
	}
	
	class Combination {
		final Set<String> words;
		Integer letterCount = 0;
		Combination (Set<String> words) {
			this.words = words;
			words.forEach(w -> letterCount += w.length());
		}
		
		@Override
		public String toString() {
			return letterCount + ": " + words.toString();
		}
	}
	
}
