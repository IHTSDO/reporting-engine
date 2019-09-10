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
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

/**
 * RP-227 Pattern KPIs
 * --------------------
 * RP-228 Redundant ungrouped roles
 * RP-229 Redundant stated groups
 * RP-232 Redundant stated IS A
 * RP-231 Newly inactivatated duplicate created in prior release
 */
public class ReleasePatternsReport extends TermServerReport implements ReportClass {
	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	AncestorsCache cache;
	String previousPreviousRelease; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(ReleasePatternsReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		runStandAlone = false; //We need to load previous previous for real
		getArchiveManager().populateReleasedFlag = true;
		getArchiveManager().populatePreviousTransativeClosure = true;
		previousPreviousRelease = getArchiveManager().getPreviousPreviousBranch(project);
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
						"Release Patterns Report",
						"This report identifies a number of potentially problematic patters, many of which are tracked as KPIs ",
						new JobParameters());
	}

	public void runJob() throws TermServerScriptException {
		info("Checking for problematic patterns...");
		
		info("Checking for redundancies...");
		checkRedundantlyStatedParents();
		checkRedundantlyStatedUngroupedRoles();
		checkRedundantlyStatedGroups();
		
		info("Checking for historical patterns");
		checkCreatedButDuplicate();
		
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
	
	private void checkRedundantlyStatedParents() throws TermServerScriptException {
		//RP-232 Redundantly stated IsA relationships
		String issueStr = "Pattern 1: Redundantly stated IsA relationships";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each ungrouped relationship to see if it subsumes any other
				for (Concept a : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
					for (Concept b :c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
						if (!a.equals(b) && cache.getAncestorsOrSelf(a).contains(b)) {
							report (c, issueStr, a, b);
						}
					}
				}
			}
		}
	}

	private void checkRedundantlyStatedUngroupedRoles() throws TermServerScriptException {
		//RP-288 Redundantly stated ungrouped roles
		String issueStr = "Pattern 2: Redundantly stated ungrouped role";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each ungrouped relationship to see if it subsumes any other
				for (Relationship a : c.getRelationshipGroupSafely(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships()) {
					for (Relationship b : c.getRelationshipGroupSafely(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships()) {
						if (!a.equals(b) && cache.getAncestorsOrSelf(a.getType()).contains(b.getType())) {
							if (cache.getAncestorsOrSelf(a.getTarget()).contains(b.getTarget())) {
								report (c, issueStr, a, b);
							}
						}
					}
				}
			}
		}
	}
	
	private void checkRedundantlyStatedGroups() throws TermServerScriptException {
		//RP-289 Redundantly stated ungrouped roles
		String issueStr = "Pattern 3: Redundantly stated group";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Test each relationship group to see if it subsumes any other
				for (RelationshipGroup a : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
					for (RelationshipGroup b : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
						if (a.getGroupId() == UNGROUPED || b.getGroupId() == UNGROUPED || a.getGroupId() == b.getGroupId()) {
							continue;
						} else {
							if (SnomedUtils.covers(a, b, cache)) {
								report (c, issueStr, a, b);
							}
						}
					}
				}
			}
		}
	}
	
	private void checkCreatedButDuplicate() throws TermServerScriptException {
		//RP-231 Pattern 21 Newly inactivatated duplicate was created in previous release
		String issueStr = "Pattern 21: Newly inactivatated duplicate was created in previous release.";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive() && 
				(c.getEffectiveTime() == null || c.getEffectiveTime().isEmpty()) &&
				c.getInactivationIndicator().equals(InactivationIndicator.DUPLICATE)) {
				//Did this concept exist in the previous previous release?
				//If not, then it was created in the previous release and immediately retired
				try {
					Concept loadedConcept = loadConcept(c, previousPreviousRelease);
					if (loadedConcept == null) {
						report (c, issueStr);
					}
				} catch (Exception e) {
					report (c, "API ERROR", "Failed to check previous previous release due to " + e.getMessage());
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
