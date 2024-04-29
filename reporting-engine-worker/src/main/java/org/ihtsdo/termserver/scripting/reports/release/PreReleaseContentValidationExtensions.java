package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PreReleaseContentValidationExtensions extends PreReleaseContentValidation {

	private static final Logger LOGGER = LoggerFactory.getLogger(PreReleaseContentValidationExtensions.class);

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		//params.put(THIS_RELEASE, "SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20220531T120000Z.zip");
		//params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220131T120000Z.zip");
		//params.put(PREV_RELEASE, "SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20200531T120000Z.zip");
		//params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip");
		params.put(MODULES, "45991000052106");

		TermServerReport.run(PreReleaseContentValidationExtensions.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Pre-release Content Validation for Extensions")
				.withDescription("A set of counts and informational queries originally run as SQL")
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		// Either specify all values or none of them. Use the XOR indicator.
		if (XOR(PREV_RELEASE, PREV_DEPENDENCY, THIS_RELEASE, THIS_DEPENDENCY)) {
			throw new TermServerScriptException ("Either specify ALL [PrevRelease, PrevDependency, ThisRelease, ThisDependency], or NONE of them to run against the in-flight project.");
		}

		if (!StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) && StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
			throw new TermServerScriptException("Module filter must be specified when working with published archives");
		}

		super.init(run);
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		if (getProject().getKey().equals("MAIN")) {
			throw new TermServerScriptException ("This report cannot be run on MAIN. Use 'Pre-Release Content Validation' instead.");
		}

		prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
		if (StringUtils.isEmpty(prevDependency)) {
			prevDependency = getProject().getMetadata().getPreviousDependencyPackage();
			if (StringUtils.isEmpty(prevDependency)) {
				throw new TermServerScriptException("Previous dependency package not populated in branch metadata for " + getProject().getBranchPath());
			}
		}

		thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
		if (StringUtils.isEmpty(thisDependency)) {
			thisDependency = getProject().getMetadata().getDependencyPackage();
			if (StringUtils.isEmpty(thisDependency)) {
				throw new TermServerScriptException("Dependency package not populated in branch metadata for " + getProject().getBranchPath());
			}
		}

		if (StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
			String defaultModule = project.getMetadata().getDefaultModuleId();
			if (StringUtils.isEmpty(defaultModule)) {
				throw new TermServerScriptException("Unable to recover default moduleId from project: " + project.getKey());
			}
			moduleFilter = Collections.singletonList(defaultModule);
		}

		LOGGER.info("Setting previous dependency archive: " + prevDependency);
		setDependencyArchive(prevDependency);

		super.loadProjectSnapshot(fsnOnly);
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		LOGGER.info("Setting dependency archive: " + thisDependency);
		setDependencyArchive(thisDependency);

		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
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
}
