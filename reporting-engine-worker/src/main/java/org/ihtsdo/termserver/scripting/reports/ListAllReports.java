package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.job.JobManager;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

public class ListAllReports extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListAllReports.class);

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(ListAllReports.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
		headers="Category, Name, Description, Production Status, Tags";
		additionalReportColumns="";
	}

	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		LOGGER.info("Skipping Snapshot load - not required for this report");
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Reports")
				.withDescription("This report lists all reports available to users, along with their descriptions, production status and tags. ")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withTag(MS)
				.withParameters(new JobParameters())
				.build();
	}

	public void runJob() throws TermServerScriptException {
		JobManager jobManager = new JobManager();
		jobManager.init();
		for (JobType jobType: jobManager.getMetadata().getJobTypes()) {
			for (JobCategory jobCat : jobType.getCategories()) {
				for (Job job : jobCat.getJobs()) {
					countIssue(null);
					report(PRIMARY_REPORT, jobCat.getName(), job.getName(), job.getDescription(), job.getProductionStatus().toString(), job.getTags());
				}
			}
		}
	}

}
