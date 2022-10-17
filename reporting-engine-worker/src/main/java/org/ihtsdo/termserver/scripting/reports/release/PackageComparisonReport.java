package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * 
 */
public class PackageComparisonReport extends TermServerReport implements ReportClass {
	
	public static final String A_RELEASE = "A Release";
	public static final String B_RELEASE = "B Release";
	
	//public static final String PREV_DEPENDENCY = "Previous Dependency";
	//public static final String THIS_DEPENDENCY = "This Dependency";
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(PackageComparisonReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"RefsetId, Active, isNew, Mapping, UUID",
				"RefsetId, Active, isNew, Concept, UUID"};
		String[] tabNames = new String[] {
				"Release A Summary",
				"Release B Summary"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(A_RELEASE).withType(JobParameter.Type.STRING)
				.add(B_RELEASE).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();
				
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Package Comparison Report")
				.withDescription("This report compares two packages (zip archives) using Unix scripts with output captured into usual Google Sheets")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withExpectedDuration(40)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//Kick off scripts here
	}
	
}