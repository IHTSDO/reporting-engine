package org.ihtsdo.termserver.scripting.reports.oneOffs;

import java.io.File;
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
		additionalReportColumns = "FSN, Semtag, isActive, EN Description, Issue, NL Description";
		inputFiles.add(0, new File("resources/nl_semantic_tag_translations.tsv"));
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
				.build();
	}

	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActive()) {
				continue;
			}
			String acronymsStr = c.getDescriptions(ActiveState.ACTIVE).stream()
					.map(d -> d.getTerm())
					.filter(t -> isAcronym(t))
					.collect(Collectors.joining(",\n"));
			if (!StringUtils.isEmpty(acronymsStr)) {
				report(c, acronymsStr);
				countIssue(c);
			}
		}
	}

	private boolean isAcronym(String term) {
		return term.equals(term.toUpperCase()) && allLetters(term);
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
