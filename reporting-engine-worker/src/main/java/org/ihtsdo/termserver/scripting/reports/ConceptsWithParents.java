package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

public class ConceptsWithParents extends TermServerReport implements ReportClass {
	
	Map<String, Map<String, Concept>> semanticTagHierarchy = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermServerReport.run(ConceptsWithParents.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns="FSN, SemTag, DEF_STATUS, Immediate Stated Parent, Immediate Inferred Parents ,Inferred Grand Parents";
		super.init(run);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(HIERARCHIES).withType(JobParameter.Type.CONCEPT_LIST).withMandatory().build();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES),
						"Concepts with Parents",
						"This report lists all parents and grandparents of concepts in the specified hierarchies. " +
						"Note that since this report is not listing any problems, the 'Issues' count will always be 0.",
						params);
	}

	public void runJob() throws TermServerScriptException {

		Set<Concept> conceptsOfInterest = new HashSet<>();
		for (String hierarchyStr : jobRun.getMandatoryParamValue(HIERARCHIES).split(COMMA)) {
			Concept hierarchy = gl.getConcept(hierarchyStr);
			conceptsOfInterest.addAll(gl.getDescendantsCache().getDescendentsOrSelf(hierarchy));
		}
		
		for (Concept c : conceptsOfInterest) {
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			List<Concept> statedParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
			String statedParentsStr = statedParents.stream().map(p->p.toString())
					.collect(Collectors.joining(",\n"));
			List<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
			String parentsStr = parents.stream().map(p->p.toString())
					.collect(Collectors.joining(",\n"));
			String parentsParentsStr = parents.stream()
					.flatMap(p->p.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream())
					.map(pp->pp.toString())
					.collect(Collectors.joining(",\n"));
			String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			report (c, defn, statedParentsStr, parentsStr, parentsParentsStr);
			incrementSummaryInformation("Concepts reported");
		}
	}



}
