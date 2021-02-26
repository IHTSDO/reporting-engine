package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.dao.ReportConfiguration.ReportFormatType;
import org.ihtsdo.termserver.scripting.dao.ReportConfiguration.ReportOutputType;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

/**
 * RP-390 Summary Component Stats for Extensions
 * */
public class SummaryComponentStatsExtensions extends SummaryComponentStats {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		params.put(THIS_RELEASE, "SnomedCT_BelgiumExtensionRF2_PRODUCTION_20200915T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_BelgiumExtensionRF2_PRODUCTION_20200315T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip");
		params.put(MODULES, "11000172109");
		//params.put(REPORT_OUTPUT_TYPES, "S3");
		//params.put(REPORT_FORMAT_TYPE, "JSON");
		TermServerReport.run(SummaryComponentStatsExtensions.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREV_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportFormatType.CSV.name())
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Summary Component Stats for Extensions")
				.withDescription("This report lists component changes per major hierarchy, optionally filtered by moduleId (comma separate if multiple).   You can either specify two releases to compare as archives stored in S3 " + 
				"(eg SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip) or leave them blank to compare the current delta to the previous release as specified " +
				"by that branch.   Note that extensions require dependency packages to also be stated.  Use 'Summary Component Stats' for the US Edition, however.")
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.build();
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
		prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
		if (StringUtils.isEmpty(prevDependency)) {
			throw new NotImplementedException("TODO: Find previous release branch dependency");
		}
		setDependencyArchive(prevDependency);
		
		thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
		if (StringUtils.isEmpty(thisDependency)) {
			thisDependency = getProject().getMetadata().getDependencyPackage();
		}
		getArchiveManager().setLoadDependencyPlusExtensionArchive(true);
		super.loadProjectSnapshot(fsnOnly);
	}
	
	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}
}
