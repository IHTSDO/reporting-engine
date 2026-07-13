package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RP-390 Summary Component Stats for Extensions
 * */
public class SummaryComponentStatsExtensions extends SummaryComponentStats {

	public static void main(String[] args) throws TermServerScriptException{
		Map<String, String> params = new HashMap<>();
		params.put(THIS_RELEASE, "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20250315T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20250215T120000Z.zip");
		params.put(MODULES, "57091000202101,51000202101,57101000202106");  //NZ Module
		TermServerScript.run(SummaryComponentStatsExtensions.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Summary Component Stats for Extensions")
				.withDescription("This report lists component changes per major hierarchy, optionally filtered by moduleId " +
				"(comma separate if multiple). You can either specify two releases (with their dependencies) to compare as archives stored in S3 " + 
				"or leave ALL FIELDS blank to compare the current delta to the previous release as specified " +
				"by that project branch.  This report is for projects packaged as extensions only.  Use 'Summary Component Stats for Editions' for the International and US Edition.")
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withExpectedDuration(30)
				.build();
	}
	
	@Override
	protected void loadProjectSnapshot() throws TermServerScriptException {

		if (StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
			String defaultModule = project.getMetadata().getDefaultModuleId();
			if (StringUtils.isEmpty(defaultModule)) {
				throw new TermServerScriptException("Unable to recover default moduleId from project: " + project.getKey());
			}
			moduleFilter = Collections.singletonList(defaultModule);
		}

		super.loadProjectSnapshot();
	}

}
