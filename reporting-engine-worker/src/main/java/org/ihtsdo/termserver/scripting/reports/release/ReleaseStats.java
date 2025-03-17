package org.ihtsdo.termserver.scripting.reports.release;

import java.text.DecimalFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ihtsdo.termserver.scripting.reports.release.CrossoverUtils.TEST_RESULTS.ROLES_CROSSOVER;
import static org.ihtsdo.termserver.scripting.reports.release.CrossoverUtils.TEST_RESULTS.ROLEGROUPS_CROSSOVER;

/**
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * Update: Adding a 2nd report to determine how many sufficiently defined concepts are affected by an IP
 * */
public class ReleaseStats extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseStats.class);

	private static final String STD_HEADER = "SCTID, FSN, SemTag";

	public static final String THIS_RELEASE = "This Release";
	public static final String MODULES = "Modules";
	protected Set<Concept> statedIntermediatePrimitives;
	String origProject; //We need to save this because visibility is decided per project so zip files don't get picked up.

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		TermServerScript.run(ReleaseStats.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Release Stats")
				.withDescription("This report measures a number of quality KPIs")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release QA

		origProject = run.getProject();
		if (!StringUtils.isEmpty(run.getParamValue(THIS_RELEASE))) {
			projectName = run.getParamValue(THIS_RELEASE);
			run.setProject(projectName);
		}

		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}

		String[] columnHeadings = new String[] {"Stat, count",
				"KPI, count, of which Orphanet",
				"SCTID, FSN, SemTag, Crossover",
				"SCTID, FSN, SemTag, Crossover",
				STD_HEADER,
				STD_HEADER,
				STD_HEADER};
		String[] tabNames = new String[] {	"Summary Release Stats",
				"Summary KPI Counts",
				"Role group crossovers",
				"Ungrouped crossovers",
				"Intermediate Primitives",
				"Stated Intermediate Primitives",
				"IP Inferred not Stated"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		
		LOGGER.info("Reporting General Release Stats");
		reportGeneralReleaseStats();
		
		LOGGER.info("Checking for role group crossovers");
		reportRoleGroupCrossovers();
		
		LOGGER.info("Checking for ungrouped crossovers");
		reportUngroupedCrossovers();
		
		LOGGER.info("Checking for Stated Intermediate Primitives");
		countIPs(CharacteristicType.STATED_RELATIONSHIP, SENARY_REPORT);
		
		LOGGER.info("Checking for Intermediate Primitives");
		countIPs(CharacteristicType.INFERRED_RELATIONSHIP, QUINARY_REPORT);
		
		LOGGER.info("Calculating Fully Defined %");
		countSD();
	}

	private void reportGeneralReleaseStats() throws TermServerScriptException {
		int allConcepts = 0, activeConcepts = 0, sd = 0, p = 0, activeDesc = 0, activeInf = 0;
		for (Concept c : gl.getAllConcepts()) {
			allConcepts++;
			if (c.isActiveSafely()) {
				activeConcepts++;
			}
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				sd++;
			} else {
				p++;
			}
			activeDesc += c.getDescriptions(ActiveState.ACTIVE).size();
			activeInf += c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE).size();
		}
		report(PRIMARY_REPORT, "All Concepts", allConcepts);
		report(PRIMARY_REPORT, "Active Concepts", activeConcepts);
		report(PRIMARY_REPORT, "Sufficiently Defined", sd);
		report(PRIMARY_REPORT, "Primitive", p);
		report(PRIMARY_REPORT, "Active Descriptions + TextDefn", activeDesc);
		report(PRIMARY_REPORT, "Active Relationships", activeInf);
	}

	private void reportRoleGroupCrossovers() throws TermServerScriptException {
		int roleGroupCrossOvers = 0;
		Set<GroupPair> processedPairs = new HashSet<>();
		for (Concept c : gl.getAllConcepts()) {
			if (inScope(c)) {
				Collection<RelationshipGroup> groups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
				//We only need to worry about concepts with >1 role group
				if (c.isActiveSafely() && groups.size() > 1) {
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

							CrossoverUtils.TEST_RESULTS testResult = CrossoverUtils.subsumptionRoleGroupTest(left, right);
							if (testResult == ROLEGROUPS_CROSSOVER || testResult == ROLES_CROSSOVER) {
								roleGroupCrossOvers++;
								String msg = "Crossover between groups #" + left.getGroupId() + " and #" + right.getGroupId();
								report(TERTIARY_REPORT, c, msg);
								break;
							}
							processedPairs.add(new GroupPair(left, right));
						}
					}
				}
			}
		}
		report(SECONDARY_REPORT, "Role group crossovers", roleGroupCrossOvers);
	}
	
	@Override
	public String getReportName() {
		String reportName = super.getReportName();
		if (jobRun != null && jobRun.getParamValue(THIS_RELEASE) != null) {
			reportName += "_" + jobRun.getParamValue(THIS_RELEASE);
		}
		if (jobRun != null && jobRun.getParamValue(MODULES) != null) {
			reportName += "_" + jobRun.getParamValue(MODULES);
		}
		return reportName;
	}
	
	//Report cases where an ungrouped attribute also appears in a more general 
	//form, grouped
	private void reportUngroupedCrossovers() throws TermServerScriptException {
		int ungroupedCrossovers = 0;
		AncestorsCache cache = gl.getAncestorsCache();
		for (Concept c : gl.getAllConcepts()) {
			if (inScope(c)) {
				if (!c.isActiveSafely() || c.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED) == null) {
					continue;
				}
				Set<Relationship> ungroupedRels = c.getRelationshipGroup(CharacteristicType.INFERRED_RELATIONSHIP, UNGROUPED).getRelationships();
				for (Relationship ungroupedRel : ungroupedRels) {
					//Is our ungrouped relationship more specific than any grouped relationship?
					for (RelationshipGroup group : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (!group.isGrouped()) {
							continue;
						}
						for (Relationship groupedRel : group.getRelationships()) {
							if (SnomedUtils.isMoreSpecific(ungroupedRel, groupedRel, cache)) {
								report(QUATERNARY_REPORT, c, "More Specific", ungroupedRel, groupedRel);
								ungroupedCrossovers++;
							} else if (SnomedUtils.inconsistentSubsumption(ungroupedRel, groupedRel, cache)) {
								report(QUATERNARY_REPORT, c, "Inconsistent", ungroupedRel, groupedRel);
								ungroupedCrossovers++;
							}
						}
					}
				}
			}
		}
		report(SECONDARY_REPORT, "Role group ungrouped inconsistencies", ungroupedCrossovers);
	}
	
	private boolean inScope (Concept c) {
		//Are we doing any module filtering?
		if (moduleFilter == null) {
			return true;
		}
		
		//Is this concept in modules of interest or did it use to be?
		if (moduleFilter.contains(c.getModuleId())) {
			return true;
		}
		
		return false;
	}

	public void countIPs (CharacteristicType charType, int reportIdx) throws TermServerScriptException {
		int ipCount = 0;
		int orphanetIPs = 0;
		Set<Concept> intermediatePrimitives = identifyIntermediatePrimitives(gl.getAllConcepts(), charType);
		if (charType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
			statedIntermediatePrimitives = intermediatePrimitives;
		}
		for (Concept c : intermediatePrimitives) {
			if (inScope(c)) {
				ipCount++;
				report(reportIdx, c);
				
				if (gl.isOrphanetConcept(c)) {
					orphanetIPs++;
				}
				
				//Are we an IP in the inferred view but not the stated?
				if (charType.equals(CharacteristicType.INFERRED_RELATIONSHIP) && 
						!statedIntermediatePrimitives.contains(c)) {
					report(SEPTENARY_REPORT, c);
				}
			}
		}
		String statedIndicator = charType.equals(CharacteristicType.STATED_RELATIONSHIP)?" stated":"";
		report(SECONDARY_REPORT, "Number of" + statedIndicator + " Intermediate Primitives", ipCount, orphanetIPs);
	}
	
	private void countSD() throws TermServerScriptException {
		double activeConcepts = 0;
		double sufficientlyDefinedConcepts = 0;
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely()) {
				activeConcepts++;
				if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					sufficientlyDefinedConcepts++;
				}
			}
		}
		
		DecimalFormat df = new DecimalFormat("##.#%");
		double percent = (sufficientlyDefinedConcepts / activeConcepts);
		String formattedPercent = df.format(percent);
		report(SECONDARY_REPORT, "% Sufficiently Defined", formattedPercent);
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
			if (other instanceof GroupPair otherPair
					&& this.one.getGroupId() == otherPair.one.getGroupId()) {
				return this.two.getGroupId() == otherPair.two.getGroupId();
			}
			return false;
		}
		
		public int hashCode() {
			return hash;
		}
	}
}
