package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobCategory;
import org.snomed.otf.scheduler.domain.JobRun;

/**
 * MAINT-224 Check for full stop in descriptions other than text definitions
 * Allow for numbers and abbreviations
 */
public class DuplicateTermsInSubhierarchy extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermServerReport.run(DuplicateTermsInSubhierarchy.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		additionalReportColumns = "FSN, SemTag, Legacy, Description, Matched Description, Matched Concept";
	}

	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { "SubHierarchy" };
		return new Job( new JobCategory(JobCategory.RELEASE_VALIDATION),
						"Duplicate Terms",
						"Lists concepts that have the same PT or Synonyms within the same sub-hierarchy",
						parameterNames);
	}

	public void runJob() throws TermServerScriptException {
		//Am I working through multiple subHierarchies, or targetting one?
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
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				//Skip FSNs
				if (d.getType().equals(DescriptionType.FSN)) {
					continue;
				}
				//Do we already know about this term?
				Description alreadyKnown = knownTerms.get(d.getTerm());
				//We will flag this even if it's for the same concept
				if (alreadyKnown != null) {
					String legacyIssue = "N";
					if (isLegacy(d).equals("Y") && isLegacy(alreadyKnown).equals("Y") ) {
						legacyIssue = "Y";
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
					Concept alreadyKnownConcept = gl.getConcept(alreadyKnown.getConceptId());
					String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
					report (c, semTag, legacyIssue, d, alreadyKnown, alreadyKnownConcept);
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
