package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration.*;
import org.springframework.util.StringUtils;

/**
 * RP-390 Summary Component Stats for Extensions
 * */
public class SummaryComponentStatsExtensions extends SummaryComponentStats {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		/*params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		params.put(THIS_RELEASE, "SnomedCT_BelgiumExtensionRF2_PRODUCTION_20200915T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_BelgiumExtensionRF2_PRODUCTION_20200315T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip");
		params.put(MODULES, "11000172109");*/
		//params.put(REPORT_OUTPUT_TYPES, "S3");
		//params.put(REPORT_FORMAT_TYPE, "JSON");
		
		//params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20210131T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20210731T120000Z.zip");
		TermServerReport.run(SummaryComponentStatsExtensions.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREV_DEPENDENCY).withType(JobParameter.Type.STRING).withMandatory()
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportFormatType.CSV.name())
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Summary Component Stats for Extensions")
				.withDescription("This report lists component changes per major hierarchy, optionally filtered by moduleId (comma separate if multiple). You can either specify two releases (with their dependencies) to compare as archives stored in S3 " + 
				"or leave blank to compare the current delta to the previous release as specified " +
				"by that project branch.  The previous package's dependency is always required to be specified as it is not available in system metadata.  " + 
				"This report is for extensions packaged as extensions only.  Use 'Summary Component Stats' for the US Edition.")
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.build();
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
		//Either specify all values or none of them.   Use the XOR indicator
		if (XOR(PREV_RELEASE,THIS_DEPENDENCY,THIS_RELEASE)) {
			throw new TermServerScriptException ("Either specify [PrevRelease,ThisDepedency,ThisRelease], or NONE of them to run against the in-flight project.");
		}
		
		prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
		if (StringUtils.isEmpty(prevDependency)) {
			//Find previous dependency via project and previous release branches
			//Note, this information is not currently available.   See MAINT-1346
			/*prevRelease = getProject().getMetadata().getPreviousRelease();
			String releaseBranchPath = "MAIN/" + getProject().getMetadata().getCodeSystemShortName() + "/" + SnomedUtils.formatReleaseDate(prevRelease);
			Branch releaseBranch = getTSClient().getBranch(releaseBranchPath);
			prevDependency = releaseBranch.getMetadata().getDependencyPackage();
			info("Previous dependency recovered from previous release branch: " + prevDependency);*/
			throw new TermServerScriptException("Previous dependency archive must always be supplied as not available through metadata");
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
	
	private boolean XOR(String... paramValues) {
		Boolean lastValueSeenPresent = null;
		for (String paramValue : paramValues) {
			if (lastValueSeenPresent == null) {
				lastValueSeenPresent = !StringUtils.isEmpty(jobRun.getParamValue(paramValue));
			} else {
				Boolean thisValueSeenPresent = !StringUtils.isEmpty(jobRun.getParamValue(paramValue));
				if (lastValueSeenPresent != thisValueSeenPresent) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		info("Setting dependency archive: " + thisDependency);
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}
}
