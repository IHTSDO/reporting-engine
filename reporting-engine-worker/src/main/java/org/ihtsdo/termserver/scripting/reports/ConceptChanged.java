package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;

public class ConceptChanged extends TermServerReport implements ReportClass {
	
	private String ecl;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< " + ROOT_CONCEPT.toString());
		TermServerReport.run(ConceptChanged.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Def Status, TermMatched, MatchedIn, Case, AttributeDetail, SubHierarchy, SubSubHierarchy";
		super.init(run);
		ecl = run.getMandatoryParamValue(ECL);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withDefaultValue("<< " + ROOT_CONCEPT)
				.build();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION),
						"Concepts Changed",
						"This report lists all concepts changed in the current release cycle",
						params);
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : findConcepts(ecl)) {
			
		}
	}
	
}
