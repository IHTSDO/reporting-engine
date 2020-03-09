package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * RP-289 List "Special" OWL Axioms
 */
public class SpecialOWLAxioms extends TermServerReport implements ReportClass {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(SpecialOWLAxioms.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().populateReleasedFlag=true;
		headers = "SCTID, FSN, Semtag, Axiom Type, Axiom";
		additionalReportColumns="";
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Special OWL axioms")
				.withDescription("This report lists all concepts which have special OWL axioms like GCIs")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters( new JobParameters())
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			for (Axiom a : c.getAdditionalAxioms()) {
				report (c, "Additional Axiom", a);
			}
			
			for (Axiom a : c.getGciAxioms()) {
				report (c, "GCI Axiom", a);
			}
		}
	}

}