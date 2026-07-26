package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.scheduler.domain.*;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("java:S110")
public class PreReleaseContentValidationExtensions extends PreReleaseContentValidation {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();

		params.put(THIS_RELEASE, "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20250315T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_ManagedServiceNO_PRODUCTION_NO1000202_20250215T120000Z.zip");
		params.put(MODULES, "57091000202101,51000202101,57101000202106");

		TermServerScript.run(PreReleaseContentValidationExtensions.class, args, params);
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

		if (!StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) && StringUtils.isEmpty(getJobRun().getParamValue(MODULES))) {
			throw new TermServerScriptException("Module filter must be specified when working with published archives");
		}

		super.init(run);
	}

	@Override
	protected void loadProjectSnapshot() throws TermServerScriptException {
		if (getProject().getKey().equals("MAIN")) {
			throw new TermServerScriptException ("This report cannot be run on MAIN. Use 'Pre-Release Content Validation' instead.");
		}

		checkAndSetModuleFilter();
		super.loadProjectSnapshot();
	}
}
