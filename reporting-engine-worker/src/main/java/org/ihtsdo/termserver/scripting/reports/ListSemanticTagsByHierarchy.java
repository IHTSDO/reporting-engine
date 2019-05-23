package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Lists all semantic tags used in each of the top level hierarchies.
 */
public class ListSemanticTagsByHierarchy extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, BODY_STRUCTURE.toString());
		TermServerReport.run(ListSemanticTagsByHierarchy.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
		headers="Hieararchy, SemTag, Count";
		additionalReportColumns="";
	}

	@Override
	public Job getJob() {
		return new Job( new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES),
						"List Semantic Tags By Hierarchy",
						"This report lists all semantic tags used in each top level hierarchy. " +
						"Note that since this report is not listing any problems, the 'Issues' count will always be 0.",
						new JobParameters());
	}

	public void runJob() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags along with their counts
		for (Concept topLevel : ROOT_CONCEPT.getDescendents(IMMEDIATE_CHILD)) {
			Set<Concept> descendents = topLevel.getDescendents(NOT_SET);
			report (PRIMARY_REPORT, (Component)null, topLevel.toString(), "", descendents.size());
			Multiset<String> tags = HashMultiset.create();
			for (Concept thisDescendent : descendents) {
				tags.add(SnomedUtils.deconstructFSN(thisDescendent.getFsn())[1]);
			}
			for (String tag : tags.elementSet()) {
				report (PRIMARY_REPORT, (Component)null, "", tag, tags.count(tag));
			}
		}
	}
}
