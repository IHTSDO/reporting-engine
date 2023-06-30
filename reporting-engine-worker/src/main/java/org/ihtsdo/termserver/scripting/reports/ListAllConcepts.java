package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ListAllConcepts extends TermServerReport implements ReportClass {
	
	public static String NEW_CONCEPTS_ONLY = "New Concepts Only";
	private boolean newConceptsOnly = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(ECL, "<<84757009 |Epilepsy (disorder)|"); 
		params.put(ECL, "*");
		params.put(NEW_CONCEPTS_ONLY, "true");
		TermServerReport.run(ListAllConcepts.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		this.getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns="FSN, SemTag, Defn, Descriptions, Inferred Expression, Stated Expression, Parents";
		super.init(run);
		newConceptsOnly = run.getMandatoryParamBoolean(NEW_CONCEPTS_ONLY);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(NEW_CONCEPTS_ONLY).withType(Type.BOOLEAN).withDefaultValue(false)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Concepts")
				.withDescription("This report lists all concepts that match a given ECL, with descriptions, parents and the inferred expression included." +
						"The issues count will show the number of concepts reported.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		List<Concept> concepts = SnomedUtils.sortFSN(findConcepts(subsetECL));
		for (Concept c : concepts) {
			if (!c.isActive() || 
					(newConceptsOnly && c.isReleased())) {
				continue;
			}
			String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			String parents = getParentsStr(c);
			String descriptionsStr = SnomedUtils.getDescriptions(c);
			String expressionInf = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
			String expressionSta = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			report(c, defn, descriptionsStr, expressionInf, expressionSta, parents);
			incrementSummaryInformation("Concepts reported");
			countIssue(c);
		}
	}

	private String getParentsStr(Concept c) {
		Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		return parents.stream().map(p->p.toString())
				.collect(Collectors.joining(",\n"));
	}

}
