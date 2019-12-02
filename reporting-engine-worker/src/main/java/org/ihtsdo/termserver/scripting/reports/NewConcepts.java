package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * RP-166 List all new concepts
 */
public class NewConcepts extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		TermServerReport.run(NewConcepts.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().populateReleasedFlag=true;
		headers = "SCTID, FSN, Semtag";
		additionalReportColumns="";
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(ROOT_CONCEPT)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List new concepts")
				.withDescription("This report lists all concepts (optionally in the given subhierarchy) created in the current authoring cycle")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			if (!c.isReleased() && inScope(c)) {
				report (c);
				countIssue(c);
			}
		}
	}

}