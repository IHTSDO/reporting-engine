package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * QI-31
 * Once remodelling is complete, a "Post Changes" check that can be run is to 
 * check for any concepts that still have immediate fully defined parents.
 * 
 * SUBST-153 Also uses this report.
 * RP-240 Make available in Reporting Platform
 */
public class FullyDefinedParentsInSubHierarchy extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, "105590001"); // Substance
		TermServerReport.run(FullyDefinedParentsInSubHierarchy.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		additionalReportColumns = "FSN, SemTag, Stated Parents, Calculated PPPs";
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(ROOT_CONCEPT)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.GENERAL_QA))
				.withName("Check for Fully Defined Parents")
				.withDescription("This report lists all concepts in the specified subhierarchy which have one or more fully defined stated parents.  The issues count displays the number of concepts found.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		nextConcept:
		for (Concept c : gl.getDescendantsCache().getDescendents(subHierarchy)) {
			for (Concept parent : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
				if (parent.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					String parentStr = c.getParents(CharacteristicType.STATED_RELATIONSHIP)
							.stream()
							.map(p -> defStatus(p) + p.toString())
							.collect(Collectors.joining(", \n"));
					List<Concept> PPPs = determineProximalPrimitiveParents(c);
					String PPPStr = PPPs.stream()
							.map(p -> p.toString())
							.collect(Collectors.joining(", \n"));
					report (c, parentStr, PPPStr);
					countIssue(c);
					continue nextConcept;
				}
			}
		}
	}

	private String defStatus(Concept c) {
		switch (c.getDefinitionStatus()) {
		case FULLY_DEFINED : return "[FD] ";
		case PRIMITIVE : return "[P] ";
		default: return "[?] ";
		}
	}
	
}
