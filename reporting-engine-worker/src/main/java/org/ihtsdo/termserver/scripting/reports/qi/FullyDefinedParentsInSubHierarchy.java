package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-31
 * Once remodelling is complete, a "Post Changes" check that can be run is to
 * check for any concepts that still have immediate fully defined parents.
 *
 * SUBST-153 Also uses this report.
 * RP-240 Make available in Reporting Platform
 * RP-887 Make available to MS Customers and upgrade to work with ECL
 */
public class FullyDefinedParentsInSubHierarchy extends TermServerReport implements ReportClass {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 105590001"); // Substance
		TermServerScript.run(FullyDefinedParentsInSubHierarchy.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		additionalReportColumns = "FSN, SemTag, Stated Parents, Stated Parents' Module, Calculated PPPs";
		ReportSheetManager.setTargetFolderId(GFOLDER_QI);
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Check for Fully Defined Parents")
				.withDescription("This report lists all concepts in the specified subhierarchy which have one or more fully defined stated parents.  The issues count displays the number of concepts found.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Set<Concept> conceptsOfInterest = findConcepts(subsetECL)
				.stream()
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		
		for (Concept c : conceptsOfInterest) {
			for (Concept parent : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
				if (parent.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					reportFullyDefinedParents(c);
					break;
				}
			}
		}
	}

	private void reportFullyDefinedParents(Concept c) throws TermServerScriptException {
		String parentStr = c.getParents(CharacteristicType.STATED_RELATIONSHIP)
				.stream()
				.map(p -> defStatus(p) + p.toString())
				.collect(Collectors.joining(", \n"));
		String parentModStr = c.getParents(CharacteristicType.STATED_RELATIONSHIP)
				.stream()
				.map(p -> p.getModuleId())
				.collect(Collectors.joining(", \n"));
		List<Concept> proximalPrimitiveParents = determineProximalPrimitiveParents(c);
		String proxPrimParentsStr = proximalPrimitiveParents.stream()
				.map(Concept::toString)
				.collect(Collectors.joining(", \n"));
		report(c, parentStr, parentModStr, proxPrimParentsStr);
		countIssue(c);
	}

	private String defStatus(Concept c) {
		switch (c.getDefinitionStatus()) {
		case FULLY_DEFINED : return "[FD] ";
		case PRIMITIVE : return "[P] ";
		default: return "[?] ";
		}
	}
	
}
