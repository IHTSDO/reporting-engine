package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RP-390 Summary Component Stats for Extensions
 * */
public class SummaryComponentStatsExtensions extends SummaryComponentStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(SummaryComponentStatsExtensions.class);

	public static void main(String[] args) throws TermServerScriptException{
		Map<String, String> params = new HashMap<>();

		params.put(THIS_RELEASE, "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20250315T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20241201T120000Z.zip");

		params.put(PREV_RELEASE, "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20250215T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20241001T120000Z.zip");

		params.put(MODULES, "57091000202101,51000202101,57101000202106");  //NZ Module
		//REPORT_OUTPUT_TYPES, "S3"
		//REPORT_FORMAT_TYPE, "JSON"
		
		TermServerScript.run(SummaryComponentStatsExtensions.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportFormatType.CSV.name())
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
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		//Either specify all values or none of them.   Use the XOR indicator
		if (xor(PREV_RELEASE,THIS_DEPENDENCY,THIS_RELEASE,PREV_DEPENDENCY)) {
			throw new TermServerScriptException ("Either specify [PrevRelease,ThisDepedency,ThisRelease,PrevDependency], or NONE of them to run against the in-flight project.");
		}
		prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
		
		if (project.getKey().equals("MAIN") && StringUtils.isEmpty(prevDependency)) {
			throw new TermServerScriptException ("This report cannot be run on MAIN.  Use 'Summary Component Stats for Editions' instead.");
		}


		if (StringUtils.isEmpty(prevDependency)) {
			prevDependency = getProject().getMetadata().getPreviousDependencyPackage();
			if (StringUtils.isEmpty(prevDependency)) {
				throw new TermServerScriptException("Previous dependency package not populated in branch metadata for " + getProject().getBranchPath());
			}
		}

		setDependencyArchive(prevDependency);

		thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
		if (StringUtils.isEmpty(thisDependency)) {
			thisDependency = getProject().getMetadata().getDependencyPackage();
		}
		
		if (!StringUtils.isEmpty(getJobRun().getParamValue(THIS_DEPENDENCY)) 
				&& StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
			throw new TermServerScriptException("Module filter must be specified when working with published archives");
		}
		
		if (StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
			String defaultModule = project.getMetadata().getDefaultModuleId();
			if (StringUtils.isEmpty(defaultModule)) {
				throw new TermServerScriptException("Unable to recover default moduleId from project: " + project.getKey());
			}
			moduleFilter = Collections.singletonList(defaultModule);
		}

		super.loadProjectSnapshot(fsnOnly);
	}

	private boolean xor(String... paramValues) {
		Boolean lastValueSeenPresent = null;
		for (String paramValue : paramValues) {
			if (lastValueSeenPresent == null) {
				lastValueSeenPresent = !StringUtils.isEmpty(jobRun.getParamValue(paramValue));
			} else {
				Boolean thisValueSeenPresent = !StringUtils.isEmpty(jobRun.getParamValue(paramValue));
				if (!lastValueSeenPresent.equals(thisValueSeenPresent)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		LOGGER.info("Setting dependency archive: {}", thisDependency);
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}
}
