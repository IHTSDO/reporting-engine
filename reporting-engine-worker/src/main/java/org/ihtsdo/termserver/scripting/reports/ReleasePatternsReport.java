package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;

/**
 * RP-227 Pattern KPIs
 * 
 * RP-228 Redundant ungrouped roles
 */
public class ReleasePatternsReport extends TermServerReport implements ReportClass {
	
	Concept subHierarchy = ROOT_CONCEPT;
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	AncestorsCache cache;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(ReleasePatternsReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		getArchiveManager().populateReleasedFlag = true;
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Details",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		cache = gl.getAncestorsCache();
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		return new Job( new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION),
						"Release Patters Report",
						"This report identifies a number of potentially problematic patters, many of which are tracked as KPIs ",
						new JobParameters());
	}

	public void runJob() throws TermServerScriptException {
		info("Checking for problematic patterns...");
		checkRedundantlyStatedUngroupedRoles();
		
		info("Checks complete, creating summary tag");
		populateSummaryTab();
		
		info("Summary tab complete, all done.");
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(e -> e.getValue())
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
	}

	private void checkRedundantlyStatedUngroupedRoles() throws TermServerScriptException {
		//RP-288 Redundantly stated ungrouped roles
		String issueStr = "Redundantly stated ungrouped role";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each ungrouped realtionship to see if it subsumes any other
				for (Relationship a : c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships()) {
					for (Relationship b : c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships()) {
						if (a.equals(b) || a.getType().equals(IS_A) || b.getType().equals(IS_A)) {
							continue;
						} else {
							if (cache.getAncestorsOrSelf(a.getType()).contains(b.getType())) {
								if (cache.getAncestorsOrSelf(a.getTarget()).contains(b.getTarget())) {
									report (c, issueStr, a, b);
								}
							}
						}
					}
				}
			}
		}
	}

	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	protected void report (Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		super.report (PRIMARY_REPORT, c, details);
	}

}
