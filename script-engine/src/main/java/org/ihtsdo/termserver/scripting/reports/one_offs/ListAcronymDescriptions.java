package org.ihtsdo.termserver.scripting.reports.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ListAcronymDescriptions extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, Object> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "Y");
		TermServerReport.run(ListAcronymDescriptions.class, params, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1bHVd-cWbcafa3alwf5nmSOVREHYbpOMP"; //MS AdHoc Reports
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, SemTag, Acryonym",
				"SCTID, FSN, SemTag, Acronym",
				"SCTID, FSN, SemTag, Acronym",
				"SCTID, FSN, SemTag, Acronym",
				"SCTID, FSN, SemTag, Acronym"};
		String[] tabNames = new String[] {	"Acronyms without Expansion",
				"Acryonyms with Capitalised Expansion",
				"Acryonyms used within term",
				"Acryonyms oddly expanded",
				"Acryonyms correctly expanded"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("List Concepts with Acronyms")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		//Set<Description> alreadyReported = new HashSet<>();
		//Concept c = gl.getConcept("716601009"); {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActive()) {
				continue;
			}
			String acronymsStr = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.filter(t -> isAcronym(t))
					.collect(Collectors.joining(",\n"));
			if (!StringUtils.isEmpty(acronymsStr)) {
				report(PRIMARY_REPORT, c, acronymsStr);
				countIssue(c);
			}
			
			acronymsStr = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.filter(t -> t.contains(" - "))
					.filter(t -> hasCapitalisedExpansion(t))
					.collect(Collectors.joining(",\n"));
			if (!StringUtils.isEmpty(acronymsStr)) {
				report(SECONDARY_REPORT, c, acronymsStr);
				countIssue(c);
			}
			
			acronymsStr = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.filter(t -> containsAcronym(t))
					.collect(Collectors.joining(",\n"));
			if (!StringUtils.isEmpty(acronymsStr)) {
				report(TERTIARY_REPORT, c, acronymsStr);
				countIssue(c);
			}
			
			acronymsStr = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.filter(t -> !isAcronym(t))
					.filter(t -> t.contains(" - "))
					.filter(t -> startsWithAcronym(t))
					.filter(t -> !hasCapitalisedExpansion(t))
					.collect(Collectors.joining(",\n"));
			if (!StringUtils.isEmpty(acronymsStr)) {
				report(QUATERNARY_REPORT, c, acronymsStr);
				countIssue(c);
			}
			
			acronymsStr = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.filter(t -> t.contains(" - "))
					.filter(t -> startsWithAcronym(t))
					.filter(t -> !hasCapitalisedExpansion(t))
					.collect(Collectors.joining(",\n"));
			if (!StringUtils.isEmpty(acronymsStr)) {
				report(QUINARY_REPORT, c, acronymsStr);
				countIssue(c);
			}
		}
	}

	private boolean startsWithAcronym(String term) {
		boolean lastCharacterWasCapital = false;
		boolean twoOrMoreConsecutiveCapitalsEncountered = false;
		int idxLastChar = term.length() - 1;
		for (int i=0; i<term.length(); i++) {
			if (Character.isUpperCase(term.charAt(i))) {
				if (lastCharacterWasCapital) {
					twoOrMoreConsecutiveCapitalsEncountered = true;
				} else {
					lastCharacterWasCapital = true;
				}
			} else if (Character.isAlphabetic(term.charAt(i)) && Character.isLowerCase(term.charAt(i))) {
				return false;
			}
			
			if (twoOrMoreConsecutiveCapitalsEncountered 
					&& (i == idxLastChar || term.charAt(i) == ' ')) {
				return true;
			}
		}
		return false;
	}

	private boolean hasCapitalisedExpansion(String term) {
		//Our cut point is the first " - "  We need everything left of that
		//to be a capital, and at least one letter to the right to be a capital
		int cutPoint = term.indexOf(" - ");
		String left = term.substring(0, cutPoint);
		if (!left.equals(left.toUpperCase())) {
			return false;
		}
		String right = term.substring(0, cutPoint);
		return !right.equals(right.toLowerCase()); 
	}

	private boolean isAcronym(String term) {
		return term.equals(term.toUpperCase()) && allLetters(term);
	}
	
	private boolean containsAcronym(String term) {
		if (isAcronym(term)) {
			return false;
		}
		boolean lastCharacterWasCapital = false;
		boolean twoOrMoreConsecutiveCapitalsEncountered = false;
		int idxLastChar = term.length() - 1;
		
		//Skip as many capital letters as we have
		int i = 0;
		while (Character.isUpperCase(term.charAt(i)) && i <= idxLastChar) {
			i++;
		}

		for (; i<term.length(); i++) {
			char c = term.charAt(i);
			if (Character.isUpperCase(c)) {
				if (lastCharacterWasCapital) {
					twoOrMoreConsecutiveCapitalsEncountered = true;
				} else {
					lastCharacterWasCapital = true;
				}
			} else {
				if (c == ' ') {
					lastCharacterWasCapital = false;
					continue;
				} else if (!Character.isAlphabetic(c)) {
					//We'll just skip these without halting our consideration of what is an acronym
					continue;
				} else if (Character.isLowerCase(c)) {
					lastCharacterWasCapital = false;
					continue;
				}
			}
			
			if (twoOrMoreConsecutiveCapitalsEncountered 
					&& (i == idxLastChar || term.charAt(i) == ' ')) {
				return true;
			}
		}
		return false;
	}

	private boolean allLetters(String term) {
		for (char c : term.toCharArray()) {
			if (!Character.isLetter(c)) {
				return false;
			}
		}
		return true;
	}
}
