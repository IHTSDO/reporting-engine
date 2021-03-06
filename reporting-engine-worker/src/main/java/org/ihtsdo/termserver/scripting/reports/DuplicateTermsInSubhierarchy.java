package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

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
		params.put(NEW_ISSUES_ONLY, "Y");
		params.put(PT_ONLY, "Y");
		TermServerReport.run(DuplicateTermsInSubhierarchy.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		additionalReportColumns = "FSN, SemTag, Legacy, Description, Matched Description, Matched Concept";
		/*
		whitelist.add("764498003");
		whitelist.add("738993004");
		whitelist.add("738991002");
		whitelist.add("421532009");
		whitelist.add("739007000");
		whitelist.add("385087003");
		whitelist.add("385286003");
		whitelist.add("739000003");
		whitelist.add("738998008");
		whitelist.add("385064006");
		whitelist.add("421079001");
		whitelist.add("385049006");
		whitelist.add("385055001");
		whitelist.add("758679000");
		whitelist.add("734821002");
		whitelist.add("738945005");
		whitelist.add("763825005");
		whitelist.add("738985004");
		whitelist.add("738948007");
		whitelist.add("225770002");
		whitelist.add("264255005");
		whitelist.add("418283001");
		whitelist.add("420317006");
		whitelist.add("422145002");
		whitelist.add("225780003");
		whitelist.add("255582007");
		whitelist.add("260548002");
		whitelist.add("421257003");
		whitelist.add("421521009");
		whitelist.add("419747000");
		whitelist.add("421134003");
		whitelist.add("421538008");
		whitelist.add("700477004");
		whitelist.add("263887005");
		whitelist.add("736678006");
		whitelist.add("736680000");
		whitelist.add("764462006");
		whitelist.add("736853009");
		whitelist.add("733005001");
		whitelist.add("732995004");
		whitelist.add("733022004");
		whitelist.add("733007009");
		whitelist.add("733019001");
		whitelist.add("732987003");
		whitelist.add("733001005");
		whitelist.add("733014006");
		*/
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(ROOT_CONCEPT)
				.add(NEW_ISSUES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(PT_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true).build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Duplicate Terms")
				.withDescription("This report lists concepts that have the same preferred term (or optionally, synonyms) within the same sub-hierarchy. " +
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
		if (subHierarchy.equals(ROOT_CONCEPT)) {
			for (Concept majorHierarchy : subHierarchy.getDescendents(IMMEDIATE_CHILD)) {
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
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
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
