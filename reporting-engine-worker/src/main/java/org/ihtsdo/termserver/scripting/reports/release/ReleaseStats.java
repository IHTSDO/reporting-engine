package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.util.StringUtils;

/**
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * Update: Adding a 2nd report to determine how many sufficiently defined concepts are affected by an IP
 * */
public class ReleaseStats extends TermServerReport implements ReportClass {
	
	public static final String IP = "IP";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		params.put(PROJECT, "20170731");
		TermServerReport.run(ReleaseStats.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters();
		return new Job(	new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS),
						"Release Stats",
						"This report measures a number of quality KPIs",
						params);
	}

	
	public void runJob() throws TermServerScriptException {
		
		info("Checking for role group crossovers");
		reportRoleGroupCrossovers();
		
		info("Checking for ungrouped crossovers");
		reportUngroupedCrossovers();
		
		info("Checking for Intermediate Primitives");
		countIPs();
		
	}

	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {",KPI, count", 
												"SCTID, FSN, SemTag, Crossover",
												"SCTID, FSN, SemTag, Crossover",
												"SCTID, FSN, SemTag"};
		String[] tabNames = new String[] {	"Summary Counts", 
											"Role group crossovers",
											"Ungrouped crossovers",
											"Intermediate Primitives"};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void reportRoleGroupCrossovers() throws TermServerScriptException {
		int roleGroupCrossOvers = 0;
		Set<GroupPair> processedPairs = new HashSet<>();
		for (Concept c : gl.getAllConcepts()) {
			Collection<RelationshipGroup> groups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
			//We only need to worry about concepts with >1 role group
			if (c.isActive() && groups.size() > 1) {
				processedPairs.clear();
				String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				//Test every group against every other group
				for (RelationshipGroup left : groups) {
					if (!left.isGrouped()) {
						continue;
					}
					for (RelationshipGroup right : groups) {
						if (left.getGroupId()==right.getGroupId() || !right.isGrouped()) {
							continue;
						}
						//Have we already processed this combination in the opposite order?
						if (processedPairs.contains(new GroupPair(right, left))) {
							continue;
						}
						switch (CrossoverUtils.subsumptionRoleGroupTest(left, right)) {
							case ROLEGROUPS_CROSSOVER :
							case ROLES_CROSSOVER:	
												roleGroupCrossOvers++;
												String msg = "Crossover between groups #" + left.getGroupId() + " and #" + right.getGroupId();
												report (SECONDARY_REPORT, c, semTag, msg);
												break;
							default:
						}
						processedPairs.add(new GroupPair(left, right));
					}
				}
			}
		}
		report (PRIMARY_REPORT, null, "Role group crossovers", roleGroupCrossOvers);

	}
	
	//Report cases where an ungrouped attribute also appears in a more general 
	//form, grouped
	private void reportUngroupedCrossovers() throws TermServerScriptException {
		int ungroupedCrossovers = 0;
		AncestorsCache cache = gl.getAncestorsCache();
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive() || c.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED) == null) {
				continue;
			}
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			List<Relationship> ungroupedRels = c.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED).getRelationships();
			for (Relationship ungroupedRel : ungroupedRels) {
				//Is our ungrouped relationship more specific than any grouped relationship?
				for (RelationshipGroup group : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
					if (!group.isGrouped()) {
						continue;
					}
					for (Relationship groupedRel : group.getRelationships()) {
						if (SnomedUtils.isMoreSpecific(ungroupedRel, groupedRel, cache)) {
							report (TERTIARY_REPORT, c, semTag, "More Specific", ungroupedRel, groupedRel);
							ungroupedCrossovers++;
						} else if (SnomedUtils.inconsistentSubsumption(ungroupedRel, groupedRel, cache)) {
							report (TERTIARY_REPORT, c, semTag, "Inconsistent", ungroupedRel, groupedRel);
							ungroupedCrossovers++;
						}
					}
				}
			}
		}
		report (PRIMARY_REPORT, null, "Role group ungrouped inconsistencies", ungroupedCrossovers);
	}

	public void countIPs () throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (!c.getConceptId().equals("210416002")) {
			//	continue;
			}
			//We're only interested in fully defined (QI project includes leaf concepts)
			if (c.isActive() &&
				c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//Get a list of all my primitive ancestors
				List<Concept> proxPrimParents = gl.getAncestorsCache().getAncestors(c).stream()
						.filter(a -> a.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE))
						.collect(Collectors.toList());
				//Do those ancestors themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					if (containsFdConcept(gl.getAncestorsCache().getAncestors(thisPPP))) {
						if (StringUtils.isEmpty(thisPPP.getIssues())) {
							String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
							report(QUATERNARY_REPORT, thisPPP, semTag);
						}
						thisPPP.setIssue(IP);
					} 
				}
			}
		}
		int ipCount = 0;
		int orphanetIPs = 0;
		for (Concept c : gl.getAllConcepts()) {
			if (c.getIssues() != null && c.getIssues().equals(IP)) {
				ipCount++;
				if (gl.isOrphanetConcept(c)) {
					orphanetIPs++;
				}
			}
		}
		report (PRIMARY_REPORT, null, "Number of Intermediate Primitives", ipCount);
		report (PRIMARY_REPORT, null, "Of which Orphanet", orphanetIPs);
	}
	
	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
	
	class GroupPair {
		RelationshipGroup one;
		RelationshipGroup two;
		int hash;
		GroupPair (RelationshipGroup one, RelationshipGroup two) {
			this.one = one;
			this.two = two;
			hash = (one.toString() + two.toString()).hashCode();
		}
		
		public boolean equals(Object other) {
			if (other instanceof GroupPair) {
				GroupPair otherPair = (GroupPair)other;
				if (this.one.getGroupId() == otherPair.one.getGroupId()) {
					return this.two.getGroupId() == otherPair.two.getGroupId();
				}
			}
			return false;
		}
		
		public int hashCode() {
			return hash;
		}
	}
}
