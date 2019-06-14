package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;

/**
 * RP-171 List all new concepts in << 363787002 | Observable entity (observable entity)
 * and << 386053000 | Evaluation procedure (procedure)
 */
public class LoincReport extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		TermServerReport.run(LoincReport.class, args, new HashMap<>());
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().populateReleasedFlag=true;
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, SemTag",
				"SCTID, FSN, SemTag"};
		String[] tabNames = new String[] {	"New Observable Entities",
				"New Evaluation Procedures"};
		
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job( new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES),
						"LOINC Report",
						"This report lists new concepts relevant to legal agreements relating to LOINC, specifically all new concepts in << 363787002 | Observable entity (observable entity) and << 386053000 | Evaluation procedure (procedure)",
						new JobParameters());
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : gl.getConcept("363787002 | Observable entity (observable entity)").getDescendents(NOT_SET)) {
			if (!c.isReleased()) {
				report (PRIMARY_REPORT, c);
				countIssue(c);
			}
		}
		
		for (Concept c : gl.getConcept("386053000 | Evaluation procedure (procedure)").getDescendents(NOT_SET)) {
			if (!c.isReleased()) {
				report (SECONDARY_REPORT, c);
				countIssue(c);
			}
		}
	}
}