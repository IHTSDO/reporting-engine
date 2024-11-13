package org.ihtsdo.termserver.scripting.reports.one_offs;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class MSSP_1457_NL_SemTagCheck extends TermServerReport implements ReportClass {

	private static Logger LOGGER = LoggerFactory.getLogger(MSSP_1457_NL_SemTagCheck.class);
	
	Map<String,String> semtagTranslationMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, Object> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "Y");
		TermServerReport.run(MSSP_1457_NL_SemTagCheck.class, params, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1bHVd-cWbcafa3alwf5nmSOVREHYbpOMP"; //MS AdHoc Reports
		super.init(run);
		additionalReportColumns = "FSN, Semtag, isActive, EN Description, Issue, NL Description";
		inputFiles.add(0, new File("resources/nl_semantic_tag_translations.tsv"));
	}
	
	public void postInit() throws TermServerScriptException {
		super.postInit();
		loadSemTagTranslations();
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Semantic Tag Translation Check")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		checkCaseSignificance();
	}

	public void loadSemTagTranslations() throws TermServerScriptException {
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
		LOGGER.info("File load complete, processing file...");
		for (String line : lines) {
			//Split the line up on tabs
			String[] items = line.split(TAB);
			String enTag = "(" + items[0] + ")";
			String nlTag = enTag;
			if (items.length > 1) {
				nlTag =  "(" + items[1] + ")";
			}
			semtagTranslationMap.put(enTag, nlTag);
		}
	}

	private void checkCaseSignificance() throws TermServerScriptException {
		int noTranslationCount = 0;
		//Work through all active descriptions of all hierarchies
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			Description enDesc = c.getFSNDescription("en");
			Description nlDesc = c.getFSNDescription("nl");
			if (nlDesc == null) {
				noTranslationCount++;
				continue;
			}
			String enSemTag = SnomedUtils.deconstructFSN(enDesc.getTerm())[1];
			String nlSemTag = SnomedUtils.deconstructFSN(nlDesc.getTerm())[1];
			String expectedTranslation = semtagTranslationMap.get(enSemTag);
			if (expectedTranslation == null) {
				String msg = "No translation specified for " + enSemTag + " in " + c;
				LOGGER.warn(msg);
				report(c, c.isActive()?"Y":"N", enDesc, msg);
				countIssue(c);
				noTranslationCount++;
				continue;
			}
			if (!nlSemTag.equals(expectedTranslation)) {
				report(c, c.isActive()?"Y":"N", enDesc, "Expected " + expectedTranslation + " but found " + nlSemTag, nlDesc);
				countIssue(c);
			}
		}
		LOGGER.info(noTranslationCount + " concepts did not have a translation");
	}
}
	
