package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportConfiguration;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("java:S110")
public class ReleaseStatsEditions extends SummaryComponentStats {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20260201T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip");
		params.put(REPORT_OUTPUT_TYPES, "S3");
		params.put(REPORT_FORMAT_TYPE, "JSON");
		TermServerScript.run(ReleaseStatsEditions.class, args, params);
	}

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
