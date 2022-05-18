package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Check for duplicate terms (PT only by default) within the same hierarchy.
 */
public class DuplicateTermsInSubhierarchy extends TermServerReport implements ReportClass {
	public static String NEW_ISSUES_ONLY = "New Issues Only";
	public static String PT_ONLY = "Preferred Terms Only";
	boolean newIssuesOnly = true;
	boolean ptOnly = true;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(NEW_ISSUES_ONLY, "N");
		params.put(PT_ONLY, "N");
		params.put(UNPROMOTED_CHANGES_ONLY, "Y");
		TermServerReport.run(DuplicateTermsInSubhierarchy.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		additionalReportColumns = "FSN, SemTag, Legacy, Description, Matched Description, Matched Concept";
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT)
				.add(NEW_ISSUES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(PT_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Duplicate Terms")
				.withDescription("This report lists concepts that have the same term within the same sub-hierarchy. " +
						"The 'Issues' count here reflects the number of rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		ptOnly = jobRun.getParameters().getMandatoryBoolean(PT_ONLY);
		newIssuesOnly = jobRun.getParameters().getMandatoryBoolean(NEW_ISSUES_ONLY);
		
		//Am I working through multiple subHierarchies, or targeting one?
		if (subHierarchy == null || subHierarchy.equals(ROOT_CONCEPT)) {
			for (Concept majorHierarchy : ROOT_CONCEPT.getDescendents(IMMEDIATE_CHILD)) {
				info ("Reporting " + majorHierarchy);
				reportDuplicateDescriptions(majorHierarchy);
			}
		} else {
			reportDuplicateDescriptions(subHierarchy);
		}
	}

	private void reportDuplicateDescriptions(Concept subHierarchy) throws TermServerScriptException {
		//Create a map of all not-fsn terms and check for one already known
		Map<String, Description> knownTerms = new HashMap<>();
		Acceptability acceptability = ptOnly ? Acceptability.PREFERRED : Acceptability.BOTH;
		Collection<Concept> concepts = subHierarchy == null ? gl.getAllConcepts() : subHierarchy.getDescendents(NOT_SET);
		for (Concept c : concepts) {
			//Have we white listed this concept?
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			for (Description d : c.getDescriptions(acceptability, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				//Do we already know about this term?
				Description alreadyKnown = knownTerms.get(d.getTerm());
				
				//We will flag this even if it's for the same concept
				if (alreadyKnown != null) {
					
					//Are we checking only unpromoted changes?  Either d or the already known
					//term can be unpromted to quality
					if (unpromotedChangesOnly 
							&& !unpromotedChangesHelper.hasUnpromotedChange(d)
							&& !unpromotedChangesHelper.hasUnpromotedChange(alreadyKnown)) {
						continue;
					}
					
					String legacyIssue = "N";
					if (isLegacy(d).equals("Y") && newIssuesOnly) {
						continue;
					}
					
					if (isLegacy(d).equals("Y") && isLegacy(alreadyKnown).equals("Y") ) {
						legacyIssue = "Y";
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
					Concept alreadyKnownConcept = gl.getConcept(alreadyKnown.getConceptId());
					report (c, legacyIssue, d, alreadyKnown, alreadyKnownConcept);
					countIssue(c); 
				} else {
					knownTerms.put(d.getTerm(), d);
				}
			}
		}
	}
	
	private String isLegacy(Component c) {
		return c.getEffectiveTime() == null ? "N" : "Y";
	}

}
