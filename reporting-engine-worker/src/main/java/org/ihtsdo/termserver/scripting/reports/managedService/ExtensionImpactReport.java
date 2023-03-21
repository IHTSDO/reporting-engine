package org.ihtsdo.termserver.scripting.reports.managedService;

import java.io.IOException;
import java.util.*;
import java.util.stream.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.reports.release.HistoricDataUser;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * FRI-254 A number of what were originally SQL queries now converted into a user-runnable
 * report
 * */
public class ExtensionImpactReport extends HistoricDataUser implements ReportClass {
	
	private static String INTERNATIONAL_RELEASE = "International Release Archive";
	private static String INCLUDE_UNPUBLISHED = "Include unpublished International changes";
	private static String CURRENT_DEPENDENCY_ET = "Current dependency effective time";
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(INTERNATIONAL_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20220131T120000Z.zip");
		TermServerReport.run(ExtensionImpactReport.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INTERNATIONAL_RELEASE).withType(JobParameter.Type.STRING)
				.add(INCLUDE_UNPUBLISHED).withType(JobParameter.Type.BOOLEAN)
				.add(CURRENT_DEPENDENCY_ET).withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Extension Impact Report")
				.withDescription("This report estimates the size of the changes that will need to be made when the extension is upgraded to the latest international edition")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.build();
	}

	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		
		origProject = run.getProject();
		if (!StringUtils.isEmpty(run.getParamValue(INTERNATIONAL_RELEASE))) {
			projectName = run.getParamValue(INTERNATIONAL_RELEASE);
			run.setProject(projectName);
		}
		
		summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
		//If we're working with zip packages, we'll use the HistoricDataGenerator
		//Otherwise we'll use the default behaviour
		prevRelease = getJobRun().getParamValue(PREV_RELEASE);
		if (prevRelease == null) {
			super.doDefaultProjectSnapshotLoad(fsnOnly);
		} else {
			prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
			
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
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		info("Setting dependency archive: " + thisDependency);
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}

	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}
		
		String[] columnHeadings = new String[] {"Summary Item, Count",
												"SCTID, FSN, SemTag, New Hierarchy, Old Hierarchy", 
												"SCTID, FSN, SemTag, Old FSN, Difference",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag, Defn Status Change",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag, Text Definition",
												"SCTID, FSN, SemTag"};
		String[] tabNames = new String[] {	"Summary Counts",
											"Inactivation", 
											"New Concepts",
											"Modeling",
											"Translation",
											"DefnStatus",
											"New FSNs",
											"Text Defn",
											"ICD-O"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void runJob() throws TermServerScriptException {
		

	}

	
}
