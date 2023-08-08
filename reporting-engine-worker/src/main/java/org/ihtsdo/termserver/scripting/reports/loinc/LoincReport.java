package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang3.time.DateUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.SingleTraceabilityService;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-171 List all new concepts in << 363787002 | Observable entity (observable entity)
 * and << 386053000 | Evaluation procedure (procedure)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincReport.class);

	private Set<String> semTagExclusions = new HashSet<>();
	private TraceabilityService traceabilityService;
	private SimpleDateFormat dateFormat =  new SimpleDateFormat("yyyyMMdd");
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		TermServerReport.run(LoincReport.class, args, new HashMap<>());
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh"; //LOINC
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, SemTag, EffectiveTime, Author, Task, Creation Date",
				"SCTID, FSN, SemTag, EffectiveTime, Author, Task, Creation Date"};
		String[] tabNames = new String[] {	"New Observable Entities",
				"New Evaluation Procedures"};
		semTagExclusions.add("(regime/therapy)");
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("LOINC Report")
				.withDescription("This report lists new concepts in the core module relevant to legal agreements relating to LOINC, specifically all new concepts in << 363787002 | Observable entity (observable entity) and << 386053000 | Evaluation procedure (procedure)")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		traceabilityService = new SingleTraceabilityService(jobRun, this);
		traceabilityService.setBranchPath(project.getKey());
		
		for (Concept c : gl.getConcept("363787002 | Observable entity (observable entity)").getDescendents(NOT_SET)) {
			if (!c.isReleased() && !isExcluded(c)) {
				populateTraceabilityAndReport(PRIMARY_REPORT, c);
				countIssue(c);
			}
		}
		
		for (Concept c : gl.getConcept("386053000 | Evaluation procedure (procedure)").getDescendents(NOT_SET)) {
			if (!c.isReleased() && !isExcluded(c)) {
				populateTraceabilityAndReport(SECONDARY_REPORT, c);
				countIssue(c);
			}
		}
		traceabilityService.flush();
	}
	
	private void populateTraceabilityAndReport(int tabIdx, Concept c, Object... data) throws TermServerScriptException {
		//Are we working on a published release, or "in-flight" project?
		String fromDate = null;
		//We're now working on monthly releases, so it could be anything in the last 3 months tops
		Date fromDateDate = DateUtils.addDays(new Date(),-180);
		fromDate = dateFormat.format(fromDateDate);
		traceabilityService.populateTraceabilityAndReport(fromDate, null, tabIdx, c, data);
	}

	private boolean isExcluded(Concept c) {
		String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		for (String semTagExclusion : semTagExclusions) {
			if (semTag.equals(semTagExclusion)) {
				return true;
			}
		}
		return false;
	}
}
