package org.ihtsdo.termserver.scripting.reports.release;

import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportConfiguration;

@SuppressWarnings("java:S110")
public class ReleaseStatsEditions extends SummaryComponentStats {
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportOutputType.S3.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportFormatType.JSON.name())
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DEVOPS))
				.withName("Release Stats for Editions")
				.withDescription("This report generates release statistics for the browser.")
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withExpectedDuration(30)
				.build();
	}

}
