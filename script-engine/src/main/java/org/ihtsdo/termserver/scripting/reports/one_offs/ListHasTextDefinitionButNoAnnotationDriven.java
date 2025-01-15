package org.ihtsdo.termserver.scripting.reports.one_offs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

public class ListHasTextDefinitionButNoAnnotationDriven extends TermServerReport implements ReportClass {
	
	private List<DescriptionType> typesOfInterest = List.of(DescriptionType.TEXT_DEFINITION);
	
	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(ListHasTextDefinitionButNoAnnotationDriven.class, new HashMap<>(), args);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Concepts with text definitions but no annotation")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters( new JobParameters())
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> concepts = processFile().stream()
				.map(c -> gl.getConceptSafely(c.getId()))
				.toList();
		for (Concept c : concepts) {
			List<Description> textDefs = c.getDescriptions(ActiveState.ACTIVE,typesOfInterest);
			boolean hasAnnotation = !c.getComponentAnnotationEntries().isEmpty();
			if (!textDefs.isEmpty() && !hasAnnotation) {
				String textDfnStr = textDefs.stream().map(d -> d.getTerm()).collect(Collectors.joining(",\n"));
				report(c, textDfnStr);
			}
		}
	}
}