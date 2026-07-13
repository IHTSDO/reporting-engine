package org.ihtsdo.termserver.scripting.reports.release.browser;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.reports.release.SummaryComponentStats;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportConfiguration;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("java:S110")
public class BrowserReleaseStatsGenerator extends SummaryComponentStats {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();

		params.put(THIS_RELEASE, "SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20260321T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20260221T120000Z.zip");
		params.put(MODULES, "11000220105,1601000220105");

		params.put(REPORT_OUTPUT_TYPES, "S3");
		params.put(REPORT_FORMAT_TYPE, "JSON");
		TermServerScript.run(BrowserReleaseStatsGenerator.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withMandatory()
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withMandatory()
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportOutputType.S3.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportFormatType.JSON.name())
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DEVOPS))
				.withName("Browser Release Stats Generator")
				.withDescription("This report generates release statistics for the browser.")
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withExpectedDuration(30)
				.build();
	}

}
