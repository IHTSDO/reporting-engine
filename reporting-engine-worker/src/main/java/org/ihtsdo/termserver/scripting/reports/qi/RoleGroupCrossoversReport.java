package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.reports.release.CrossoverUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-233 Role group crossovers
 * RP-632 Bring into own report and add ECL filtering.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleGroupCrossoversReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(RoleGroupCrossoversReport.class);

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "< 404684003 |Clinical finding|");
		TermServerScript.run(RoleGroupCrossoversReport.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("11i7XQyb46P2xXNBwlCOd3ssMNhLOx1m1"); //QI / Misc Analysis
		super.init(run);
		runStandAlone = false; //We need to load previous previous for real
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Groups"};
		String[] tabNames = new String[] {	"Issues"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Role Group Crossover Report")
				.withDescription("This report identifies where two or more groups contain mismatching level of specificity in attributes that share subsumption relationships between the groups.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		List<Concept> subset;
		if (subsetECL != null) {
			subset = new ArrayList<>(findConcepts(subsetECL));
		} else {
			subset = new ArrayList<>(gl.getAllConcepts());
		}
		subset.sort(Comparator.comparing(Concept::getFsn));
		
		Set<GroupPair> processedPairs = new HashSet<>();
		for (Concept c : subset) {
			/*if (c.getConceptId().equals("10311005")) {
				LOGGER.debug("here");
			}*/
			Collection<RelationshipGroup> groups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
			//We only need to worry about concepts with >1 role group
			if (c.isActive() && groups.size() > 1) {
				processedPairs.clear();
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
									String issueStr = "Crossover between group " + left.getGroupId() + " and " + right.getGroupId();
									report(c, issueStr, left + "\n" + right);
									countIssue(c);
									break;
							default:
						}
						processedPairs.add(new GroupPair(left, right));
					}
				}
			}
		}
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
