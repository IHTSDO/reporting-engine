package org.ihtsdo.termserver.scripting.pipeline.loinc.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/*
 * INFRA-15052 - Automated matching of unmapped LOINC components to SNOMED CT concepts
 */
public class LexicalSearchForMapping extends LoincScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(LexicalSearchForMapping.class);

	Map<String, UnmappedPart> unmappedPartMap = new HashMap<>();
	Map<String, ReviewComment> reviewCommentMap = new HashMap<>();
	List<LexicalConcept> lexicalConcepts = new ArrayList<>();

	private static final List<String> TYPES_TO_INCLUDE = Arrays.asList("COMPONENT", "DIVISORS");

	private static final int MAX_MATCHES = 3;

	public static void main(String[] args) throws TermServerScriptException, IOException {
		new LexicalSearchForMapping().run(args);
	}

	public void run(String[] args) throws TermServerScriptException, IOException {
		try {
			getGraphLoader().setExcludedModules(new HashSet<>());
			getArchiveManager().setLoadOtherReferenceSets(true);
			getArchiveManager().setRunIntegrityChecks(false);
			getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);  //Needed for working out if we're deleteing or inactivating
			init(args);
			loadProjectSnapshot(false);
			postInit();
			loadSupportingInformation();
			loadUnmappedParts("/Users/peter/GDrive/018_Loinc/2025/map_work/unmapped_parts.txt");
			loadUReviewComments("/Users/peter/GDrive/018_Loinc/2025/map_work/lexical_comments_20250429.tsv");
			prepareLexicalConcepts(SUBSTANCE);
			prepareLexicalConcepts(ORGANISM);
			prepareLexicalConcepts(BODY_STRUCTURE);
			searchForLexicalMatches();
		} finally {
			finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = {
				"Unmapped Part Suggestions",
				"Hopeless Cases"
		};

		String[] columnHeadings = {
				"Part Number, Part Name, Part Type, High Priority, MatchConclusion,  Method Used, Scores, Matches, Comments",
				"Part Number, Part Name, Part Type, High Priority"
		};
		super.postInit(GFOLDER_LOINC, tabNames, columnHeadings, false);
	}
	private void prepareLexicalConcepts(Concept hierarchy) throws TermServerScriptException {
		for (Concept c : hierarchy.getDescendants(NOT_SET)) {
			LexicalConcept lc = new LexicalConcept();
			lc.concept = c;
			lc.terms = new ArrayList<>();
			lc.tokenizedTerms = new HashMap<>();
			lc.allTokens = new ArrayList<>();

			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String term = d.getTerm();
				if (d.getType().equals(DescriptionType.FSN)) {
					term = SnomedUtilsBase.deconstructFSN(term)[0];
				}
				term = normalizeTerm(term);
				lc.terms.add(term);

				String[] tokens = term.split(" ");
				for (String token : tokens) {
					if (!lc.allTokens.contains(token)) {
						lc.allTokens.add(token);
					}
					lc.tokenizedTerms.computeIfAbsent(d, k -> new ArrayList<>()).add(token);
				}
			}
			lexicalConcepts.add(lc);
		}
	}

	private void searchForLexicalMatches() throws TermServerScriptException {
		for (UnmappedPart unmappedPart : unmappedPartMap.values()) {
			//We'll work through a set of successively more permissive matches
			if (!mapUsingExactMatch(unmappedPart)) {
				if (!mapUsingPartialMatch(unmappedPart)) {
					report(SECONDARY_REPORT, unmappedPart.asArray());
				}
			}
		}
	}

	private boolean mapUsingExactMatch(UnmappedPart unmappedPart) throws TermServerScriptException  {
		//It is possible to have exact matches in other hierarchies, so don't stop early
		List<LexicalConcept> exactMatches = new ArrayList<>();
		for (LexicalConcept lc : lexicalConcepts) {
			for (String term : lc.terms) {
				if (term.equals(unmappedPart.getPartName())) {
					exactMatches.add(lc);
					break;
				}
			}
		}

		//If any of these matches have been rejected, try a partial match
		return reportExactMatches(unmappedPart, exactMatches);
	}

	private boolean reportExactMatches(UnmappedPart unmappedPart, List<LexicalConcept> exactMatches) throws TermServerScriptException {
		if (exactMatches.isEmpty()) {
			return false;
		}

		ReviewComment reviewComment = reviewCommentMap.get(unmappedPart.getPartNum());
		String comment = reviewComment != null ? reviewComment.comment : "";
		String conclusion = reviewComment != null ? reviewComment.conclusion : "";

		String matchesStr = exactMatches.stream()
				.map(lc -> display(lc.concept))
				.reduce((a, b) -> a + ",\n" + b).orElse("");

		report(PRIMARY_REPORT, unmappedPart.asArray(), conclusion, "Exact Match", "20",  matchesStr, comment);
		return !conclusion.equalsIgnoreCase("EXCLUDE");
	}

	private String display(Concept concept) {
		return concept.toString() + "\n"+
				SnomedUtils.getDescriptionsToString(concept);
	}

	private boolean mapUsingPartialMatch(UnmappedPart part) throws TermServerScriptException {
		//Things we can consider in terms of confidence
		//percentage of tokens matched vs number unmatched (eg if target has more tokens than the source)
		//tokens matching in correct order
		//matching tokens across different descriptions (lower score)
		TopScoringMatches topScoringMatches = new TopScoringMatches();
		String[] tokens = part.getPartName().split(" ");
		for (LexicalConcept lc : lexicalConcepts) {
			LexicalMatchAttempt lma = attemptLexicalMatch(tokens, lc);
			topScoringMatches.addMatch(lma);
		}

		List<LexicalMatchAttempt> topMatches = topScoringMatches.getTopMatches();
		if (topMatches.isEmpty()) {
			return false;
		}

		//Do we have a review comment for this part?
		ReviewComment reviewComment = reviewCommentMap.get(part.getPartNum());
		String comment = reviewComment != null ? reviewComment.comment : "";
		//Do not report the conclusion because these related to exact matches
		String scores = topMatches.stream()
				.map(LexicalMatchAttempt::getScore)
				.map(String::valueOf)
				.reduce((a, b) -> a + ",\n" + b).orElse("");
		String matches = topMatches.stream()
				.map(lma -> lma.lc.concept.toString())
				.reduce((a, b) -> a + ",\n" + b).orElse("");
		String methodUsed = "Partial Match";
		//Part Number, Part Name, Part Type, MatchConclusion, High Priority, Method Used, Scores, Matches, Comments",
		//
		report(PRIMARY_REPORT,
				part.asArray(),
				"",
				methodUsed,
				scores,
				matches,
				comment);
		return true;
	}

	private LexicalMatchAttempt attemptLexicalMatch(String[] sourceTokens, LexicalConcept lc) {
		//Return the best scoring lexical match we get
		LexicalMatchAttempt bestMatch = null;

		//Work through all the descriptions, then see if the all tokens gives
		//us a better score
		for (Map.Entry<Description, List<String>> entry : lc.tokenizedTerms.entrySet()) {
			LexicalMatchAttempt thisMatch = attemptLexicalMatch(lc, sourceTokens, entry.getValue(), true);
			if (thisMatch.sourceTokensAvailable == 0 || (bestMatch != null && thisMatch.sourceTokensAvailable == 0)) {
				LOGGER.debug("Here");
			}
			if (bestMatch == null || thisMatch.getScore() > bestMatch.getScore()) {
				bestMatch = thisMatch;
			}
		}

		//Now see if the all tokens gives us a better score
		LexicalMatchAttempt allTokensMatch = attemptLexicalMatch(lc, sourceTokens, lc.allTokens, false);
		if (bestMatch == null || allTokensMatch.getScore() > bestMatch.getScore()) {
			bestMatch = allTokensMatch;
		}

		return bestMatch;
	}

	private LexicalMatchAttempt attemptLexicalMatch(LexicalConcept lc, String[] sourceTokens, List<String> targetTokens, boolean matchedInSameDescription) {
		LexicalMatchAttempt lma = new LexicalMatchAttempt();
		lma.lc = lc;
		lma.sourceTokensAvailable = sourceTokens.length;
		lma.targetTokensAvailable = targetTokens.size();
		lma.matchedInSameDescription = matchedInSameDescription;

		//Work through the source tokens and see how many we can match against the target Tokens
		//and note if that match happens in the order of the target tokens
		lma.sourceTokensMatched = 0;
		lma.matchedInOrder = true;
		int lastMatchLocation = -1;
		for (String sourceToken : sourceTokens) {
			int thisMatchLocation = targetTokens.indexOf(sourceToken);
			if (thisMatchLocation != NOT_FOUND) {
				lma.sourceTokensMatched++;
				if (thisMatchLocation < lastMatchLocation) {
					lma.matchedInOrder = false;
				}
			}
		}
		lma.targetTokensSpare = lma.targetTokensAvailable - lma.sourceTokensMatched;
		return lma;
	}

	protected void loadUnmappedParts(String filePath) throws TermServerScriptException, IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				// Skip empty lines
				if (line.trim().isEmpty()) continue;

				String[] tokens = line.split("\t");
				if (tokens.length != 4) {
					System.err.println("Skipping malformed line: " + line);
					continue;
				}

				String partNum = tokens[0];
				String partName = tokens[1];
				String partType = tokens[2];
				//Only interested in Components for now
				if (!TYPES_TO_INCLUDE.contains(partType)) {
					continue;
				}
				boolean highPriority = tokens[3].equalsIgnoreCase("Y");
				UnmappedPart unmappedPart = new UnmappedPart(partNum, partName, partType, highPriority);
				unmappedPartMap.put(partNum, unmappedPart);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void loadUReviewComments(String filePath) throws TermServerScriptException, IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				// Skip empty lines
				if (line.trim().isEmpty()) continue;

				String[] tokens = line.split("\t");
				String partNum = tokens[0];
				String conclusion = tokens[1];
				String comment = "";
				if (tokens.length > 2) {
					comment = tokens[2];
				}
				ReviewComment reviewComment = new ReviewComment();
				reviewComment.conclusion = conclusion;
				reviewComment.comment = comment;
				reviewCommentMap.put(partNum, reviewComment);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		throw new UnsupportedOperationException();
	}

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return null;
	}

	public class UnmappedPart {
		private String partNum;
		private String partName;
		private String partType;
		private boolean highPriority;

		public UnmappedPart(String partNum, String partName, String partType, boolean highPriority) {
			this.partNum = partNum;
			this.partName = normalizeTerm(partName);
			this.partType = partType;
			this.highPriority = highPriority;
		}

		// Getters and toString() for debugging
		public String getPartNum() { return partNum; }
		public String getPartName() { return partName; }
		public String getPartType() { return partType; }
		public boolean isHighPriority() { return highPriority; }

		@Override
		public String toString() {
			return partNum + " | " + partName + " | " + partType + " | High Priority: " + highPriority;
		}

		public String[] asArray() {
			//We've stripped out all the unwanted characters from the part name
			//So let's recover the original if we can
			String partNameStr = partName;
			if (partMap.containsKey(partNum)) {
				partNameStr = partMap.get(partNum).getPartName();
			} else {
				LOGGER.warn("Part {} {} not found in published part.csv", partNum, partName);
			}
			return new String[] {
				partNum,
				partNameStr,
				partType,
				highPriority ? "Y" : "N"
			};
		}
	}

	private String normalizeTerm(String text) {
		return text.replaceAll("[^a-zA-Z0-9 ]", " ").toLowerCase();
	}

	class LexicalConcept {
		Concept concept;
		List<String> terms;
		Map<Description, List<String>> tokenizedTerms;
		List<String> allTokens;

	}

	class ReviewComment {
		String conclusion;
		String comment;
	}

	class LexicalMatchAttempt {
		Integer score;
		LexicalConcept lc;
		int sourceTokensAvailable;
		int sourceTokensMatched;
		int targetTokensAvailable;
		int targetTokensSpare;
		boolean matchedInOrder;
		boolean matchedInSameDescription;

		int getScore() {
			if (score != null) {
				return score;
			}
			//OK we'll score it like this:
			//sourceTokensMatched / sourceTokensAvailable out of 10 points
			// minus targetTokensSpare / targetTokensAvailable out of 10 points
			// add sourceTokensMatched points for matchedInOrder
			// add sourceTokensMatched points for matchedInSameDescription
			int score = 0;
			score += (sourceTokensMatched * 10) / sourceTokensAvailable;
			score -= (targetTokensSpare * 10) / targetTokensAvailable;
			if (matchedInOrder) {
				score += sourceTokensMatched;
			}
			if (matchedInSameDescription) {
				score += sourceTokensMatched;
			}
			this.score = score;
			return score;
		}
	}

	public class TopScoringMatches {
		private final PriorityQueue<LexicalMatchAttempt> topMatches;

		public TopScoringMatches() {
			// Min-heap to keep the top 3 scores
			topMatches = new PriorityQueue<>(Comparator.comparingInt(LexicalMatchAttempt::getScore));
		}

		public void addMatch(LexicalMatchAttempt match) {
			//Don't even store a zero or lower score
			if (match.getScore() <= 0) {
				return;
			}

			topMatches.offer(match);
			if (topMatches.size() > MAX_MATCHES) {
				// Remove the lowest score if we exceed 3 matches
				topMatches.poll();
			}
		}

		public List<LexicalMatchAttempt> getTopMatches() {
			// Return the matches in descending order of score
			List<LexicalMatchAttempt> result = new ArrayList<>(topMatches);
			result.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
			return result;
		}
	}
}
