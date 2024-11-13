package org.ihtsdo.termserver.scripting.reports.one_offs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ConceptsWithSingleInferredParent extends TermServerReport implements ReportClass {

	private static Logger LOGGER = LoggerFactory.getLogger(ConceptsWithSingleInferredParent.class);
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<! 116224001 |Complication of procedure (disorder)|");
		TermServerReport.run(ConceptsWithSingleInferredParent.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
		headers="SCTID, FSN, Semtag, Defn, Parent, Parent Defn Status";
		additionalReportColumns="";
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concepts with Single Inferred Parent")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		int conceptsChecked = 0;
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL))) {
			conceptsChecked++;
			Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
			if (parents.size() == 1) {
				String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
				Concept parent = parents.iterator().next();
				String parentDefn = SnomedUtils.translateDefnStatus(parent.getDefinitionStatus());
				report(c, defn, parent, parentDefn);
				countIssue(c);
			}
		}
		LOGGER.info("Checked " + conceptsChecked + " concepts");
	}
}
