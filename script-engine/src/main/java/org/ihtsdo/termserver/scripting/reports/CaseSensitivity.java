package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * DRUGS-269, SUBST-130, MAINT-77, INFRA-2723, MAINT-345
 * Lists all case sensitive terms that do not have capital letters after the first letter
 * UPDATE: We'll also load in the existing cs_words.txt file instead of hardcoding a list of proper nouns.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSensitivity extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSensitivity.class);
	private static final String INCLUDE_SUB_ORG = "Include Substances and Organisms";
	private static final String RECENT_CHANGES_ONLY = "Recent Changes Only";

	private Set<Concept> allExclusions = new HashSet<>();
	private Set<String> whiteList = new HashSet<>();  //Note this can be both descriptions and concept SCTIDs
	private boolean recentChangesOnly = true;
	private CaseSensitivityUtils csUtils;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, Object> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		params.put(RECENT_CHANGES_ONLY, "N");
		params.put(INCLUDE_SUB_ORG, "N");
		TermServerScript.run(CaseSensitivity.class, params, args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		super.init(run);
		ReportSheetManager.setTargetFolderId(GFOLDER_RELEASE_QA);
		recentChangesOnly = run.getParameters().getMandatoryBoolean(RECENT_CHANGES_ONLY);
		additionalReportColumns = "FSN, Semtag, Description, isPreferred, CaseSignificance, Issue";
		inputFiles.add(0, new File("resources/cs_words.tsv"));
	}

	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();

		//If we include substances and organisms for cs checking, then we're not treating them as sources of truth
		boolean substancesAndOrganismsAreSourcesOfTruth = !getJobRun().getParameters().getMandatoryBoolean(INCLUDE_SUB_ORG);
		csUtils = CaseSensitivityUtils.get(substancesAndOrganismsAreSourcesOfTruth);
		
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

	@Override
	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		//Work through all active descriptions of all hierarchies
		for (Concept targetHierarchy : ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//We won't process any hierarchies that are a source of truth
			if (!csUtils.isSourceOfTruthHierarchy(targetHierarchy)) {
				List<Concept> hierarchyDescendants = new ArrayList<>(targetHierarchy.getDescendants(NOT_SET));
				LOGGER.info("Checking case significance in target hierarchy: {}", targetHierarchy);
				checkCaseSignificanceOfHierarchy(hierarchyDescendants);
				LOGGER.info("Completed hierarchy: {}", targetHierarchy);
			}
		}
	}

	private void checkCaseSignificanceOfHierarchy(List<Concept> hierarchyDescendants) throws TermServerScriptException {
		for (Concept c : hierarchyDescendants) {
			if (c.getId().equals("107580008")) {
				LOGGER.info("Checking case significance in concept: {}", c);
			}
			if (inScopeForCsChecking(c)) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (checkCaseSignificance(c, d)) {
						break;
					}
				}
			}
		}
	}

	/*
	*@return - true if we've reported an issue on this concept
	 */
	private boolean checkCaseSignificance(Concept c, Description d) throws TermServerScriptException {
		if (!inScopeForCsChecking(d)) {
			return false;
		}

		String term = d.getTerm().replace("-", " ");
		String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		String chopped = term.substring(1);
		String preferred = d.isPreferred() ? "Y" : "N";

		if (reportedForTextDefinitionRuleOrAccepted(c, d, caseSig, preferred)) {
			return true;
		}

		if (reportedForFirstLetterInfractionOrAccepted(c, d,term, caseSig, preferred)) {
			return true;
		}

		if (caseSig.equals(CS) || caseSig.equals(cI)) {
			if (checkCaseSignicanceOfCaseSensitiveTerm(c, d, chopped, preferred, caseSig, term)) {
				return true;
			}
		} else {
			if (checkCaseSignificanceOfCaseInsensitiveTerm(c, d, chopped, preferred, caseSig)) {
				return true;
			}
		}
		return false;
	}

	private boolean reportedForFirstLetterInfractionOrAccepted(Concept c, Description d, String term, String caseSig, String preferred) throws TermServerScriptException {
		String firstLetter = term.substring(0, 1);
		//Some terms of course are only one character long!
		String secondLetter = null;
		boolean isOneCharacterLong = true;
		if (term.length() > 1) {
			secondLetter = term.substring(1, 2);
			isOneCharacterLong = false;
		}

		if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase())) {
			if (!caseSig.equals(CS)) {
				//Lower case first letters must be entire term case sensitive
				report(c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
				countIssue(c);
			}
			return true;
		} else if (!isOneCharacterLong
				&& (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toUpperCase()))
				&& (Character.isLetter(secondLetter.charAt(0)) && secondLetter.equals(secondLetter.toUpperCase()))
				&& !caseSig.equals(CS)) {
			//Terms starting with acronyms should be entire term case-sensitive
			report(c, d, preferred, caseSig, "Terms starting with acronyms must be CS");
			countIssue(c);
			return true;
		}
		return false;
	}

	private boolean reportedForTextDefinitionRuleOrAccepted(Concept c, Description d, String caseSig, String preferred) throws TermServerScriptException {
		//Text Definitions must be CS
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			if (!caseSig.equals(CS)) {
				report(c, d, preferred, caseSig, "Text Definitions must be CS");
				countIssue(c);
			}
			return true;
		}
		return false;
	}

	private boolean inScopeForCsChecking(Concept c) {
		if (whiteListedConceptIds.contains(c.getId())) {
			incrementSummaryInformation(WHITE_LISTED_COUNT);
			return false;
		}

		return !(allExclusions.contains(c) || whiteList.contains(c.getId()));
	}

	private boolean inScopeForCsChecking(Description d) {
		//Are we checking only unpromoted changes?
		if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(d)) {
			return false;
		}

		if (whiteList.contains(d.getDescriptionId())) {
			return false;
		}

		if (recentChangesOnly && d.isReleasedSafely()) {
			return false;
		}
		incrementSummaryInformation("Descriptions checked");
		return true;
	}

	private boolean checkCaseSignificanceOfCaseInsensitiveTerm(Concept c, Description d, String chopped, String preferred, String caseSig) throws TermServerScriptException {
		//For case insensitive terms, we're on the look out for capital letters after the first letter
		if (!chopped.equals(chopped.toLowerCase())) {
			report(c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
			countIssue(c);
			return true;
		}

		//Or if one of our sources of truth?
		String firstWord = d.getTerm().split(" ")[0];
		if (csUtils.getSourcesOfTruth().containsKey(firstWord)) {
			report(c, d, preferred, caseSig, "Case insensitive term should be CS as per " + csUtils.getSourcesOfTruth().get(firstWord));
			countIssue(c);
			return true;
		}
		return false;
	}

	private boolean checkCaseSignicanceOfCaseSensitiveTerm(Concept c, Description d, String chopped, String preferred, String caseSig, String term) throws TermServerScriptException {
		if (chopped.equals(chopped.toLowerCase()) &&
				!csUtils.singleLetterCombo(term) &&
				!csUtils.startsWithProperNounPhrase(term) &&
				!csUtils.containsKnownLowerCaseWord(term)) {
			if (caseSig.equals(CS) && csUtils.startsWithSingleLetter(d.getTerm())) {
				//Probably OK
			} else {
				report(c, d, preferred, caseSig, "Case sensitive term does not have capital after first letter");
				countIssue(c);
				return true;
			}
		}
		return false;
	}

}
