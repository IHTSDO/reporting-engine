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
import org.snomed.otf.script.dao.ReportSheetManager;
/**
 * See FD#25496
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListAllDescriptions extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListAllDescriptions.class);

	private static String DESCRIPTION_PER_LINE = "Description Per Line";
	private Set<Concept> alreadyReported = new HashSet<>();
	private boolean descriptionPerLine = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(SUB_HIERARCHY, "43959009 |Cataract of eye due to diabetes mellitus (disorder)|");
		//params.put(SUB_HIERARCHY, "38199008 |Tooth structure (body structure)|");
		//params.put(SUB_HIERARCHY, "25093002 |Disorder of eye due to diabetes mellitus (disorder)|");
		//params.put(SUB_HIERARCHY, "19598007 |Generalized epilepsy (disorder)|");
		//params.put(SUB_HIERARCHY, "84757009 |Epilepsy (disorder)|"); 
		params.put(ECL, "<<84757009 |Epilepsy (disorder)|"); 
		TermServerReport.run(ListAllDescriptions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns="FSN, SemTag, Defn, Parents, Description";
		
		descriptionPerLine = run.getParamBoolean(DESCRIPTION_PER_LINE);
		super.init(run);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(DESCRIPTION_PER_LINE).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Descriptions")
				.withDescription("This report lists all descriptions in a given hierarchy." +
						"The issues count will show the number of concepts reported.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		List<Concept> concepts = SnomedUtils.sortFSN(findConcepts(subsetECL));
		for (Concept concept : concepts) {
			listDescriptions(concept);
		}
	}

	private void listDescriptions(Concept c) throws TermServerScriptException {
		//Have we already seen this concept?
		if (alreadyReported.contains(c)) {
			return;
		}
		
		String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		String parents = getParentsStr(c);
		
		List<Description> descriptions = c.getDescriptions(ActiveState.ACTIVE);
		SnomedUtils.prioritise(descriptions);
		
		if (descriptionPerLine) {
			report(c, defn, parents);
			for (Description d : descriptions) {
				report((Concept)null, "", "", d);
			}
		} else {
			String descriptionStr = descriptions.stream()
					.map(d -> d.getTerm())
					.collect(Collectors.joining(",\n"));
			report(c, defn, parents, descriptionStr);
		}
		
		incrementSummaryInformation("Concepts reported");
		countIssue(c);
	}

	private String getParentsStr(Concept c) {
		Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		return parents.stream().map(p->p.toString())
				.collect(Collectors.joining(",\n"));
	}

}
