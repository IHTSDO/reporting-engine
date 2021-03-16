package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-414
 */
public class NewDescriptions extends TermServerReport implements ReportClass {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	AtomicLongMap<String> componentCounts = AtomicLongMap.create();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(NewDescriptions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Detail, Detail"};
		String[] tabNames = new String[] {	
				"Description"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("New Descriptions")
				.withDescription("This report lists all discriptions (optionally filtered by ECL) created in the current authoring cycle")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//Are we running locally from command line?
		if (jobRun.getTerminologyServerUrl() == null) {
			logger.warn("No TS specified.  Using localhost");
			jobRun.setTerminologyServerUrl("http://localhost:8085/");
		}
		Collection<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept c : conceptsOfInterest) {
			
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (!d.isReleased() && inScope(d)) {
					report(PRIMARY_REPORT, c, d.getTerm(), d);
					countIssue(c);
				}
			}
		}
	}

}