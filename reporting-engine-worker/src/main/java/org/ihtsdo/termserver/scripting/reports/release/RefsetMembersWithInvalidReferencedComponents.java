package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

/**
 * <h1>Refset members with invalid referenced components</h1>
 * <p>
 * A new report added for <a href="https://jira.ihtsdotools.org/browse/FRI-582">FRI-582</a>
 * which checks for both inactive and completely invalid SCTID's in refsets,
 * the report can then distinguish whether each record that is failing has done so because it's
 * inactive or invalid.
 *
 * <p>Service Acceptance Criteria (SAC) are used to assure that authored SNOMED CT content meets specific quality measures before the content can be promoted.</p>
 *
 * <p>This report needs to:</p>
 *
 * <ul>
 * <li>It needs to be a separate report called "Refset members with invalid referenced components".</li>
 * <li>It must present the list of refset members that have inactive referenced components.</li>
 * <li>The SAC needs to validate ONLY the derivative content that is being promoted at that time (using moduleID + refsetID fields) - NOT all other derivatives for that product.</li>
 * <li>This report should not be checking ANY derivative content if the only content being promoted is core module content.</li>
 * <li>e.g: if the task being promoted contains core module changes + changes to the GP/FP refset, then the validation should check ONLY the GP/FP refset and not all 20 odd other derivative refsets that form part of the International suite.</li>
 * <li>e.g: if Estonia are promoting changes to their "eesti Abo Veregrupi Klassifikaator Simple Refset", then the validation should check only that refset content, and not all 30+ other Estonian refsets.</li>
 * <li>For the International Authoring, this should be an Optional SAC. </li>
 * <li>For Managed Service Authoring, this should be a Mandatory SAC. </li>
 * </ul>
 */
public class RefsetMembersWithInvalidReferencedComponents extends TermServerReport implements ReportClass {
	private static final Logger LOGGER = LoggerFactory.getLogger(RefsetMembersWithInvalidReferencedComponents.class);
	private static final String REPORT_NAME = "Refset members with invalid referenced components";
	private static final String REPORT_DESCRIPTION = "A report to check for both inactive and completely invalid SCTID's in refsets - the report can then distinguish whether each record that is failing has done so because it's inactive or invalid.";
	private static final String[] REPORT_TAB_NAMES = new String[]{"Issues", "Summary"};
	private static final String REPORT_TAB_ISSUES_COLUMNS = "SCTID, FSN, Semtag, Issue, Refset, Legacy, C/D/R Active, Detail, Additional Detail, Concept Inactivation";
	private static final String REPORT_TAB_SUMMARY_COLUMNS = "Issue, Count";
	private static final String ISSUE_TITLE = "Active refset member for inactive component";
	private static final String RELEASE_VALIDATION_FOLDER_ID = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ";
	private static final List<String> REF_SETS_TO_IGNORE_FOR_INACTIVE_REFERENCE_CONPONENTS = List.of("900000000000497000" //CTV3 Map
	);
	private List<Concept> sortedListOfConcepts;

	private Map<Concept, Boolean> refsetsWithChanges = new HashMap<>();
	private Map<String, Integer> summaryCounts = new HashMap<>();
	private boolean includeLegacyIssues = false;

	/**
	 * Run the report from the command line, no arguments required for this report.
	 *
	 * @param args the command line arguments
	 * @throws TermServerScriptException if there is an error in the TermServerScript
	 * @throws IOException               if there is an error in the input/output
	 */
	public static void main(String[] args) throws TermServerScriptException {
		LOGGER.debug("Running from main CLI");
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		TermServerScript.run(RefsetMembersWithInvalidReferencedComponents.class, args, params);
	}

	/**
	 * Retrieves the job configuration for the report.
	 *
	 * @return the job configuration
	 */
	@Override
	public Job getJob() {
		JobParameters jobParameters = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.add(UNPROMOTED_CHANGES_ONLY)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
				.add(MODULES) // Blank means all modules.
					.withType(JobParameter.Type.STRING)
					.withDescription("Comma separated list of modules, or blank to include all modules")
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName(REPORT_NAME)
				.withDescription(REPORT_DESCRIPTION)
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withParameters(jobParameters)
				.withTag(INT).withTag(MS)
				.build();
	}

	/**
	 * Initializes the report for execution.  Ensures that the other reference sets are loaded.
	 *
	 * @param run the job run object
	 * @throws TermServerScriptException if there is an error initializing the report
	 */
	public void init(JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(RELEASE_VALIDATION_FOLDER_ID);
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		if (unpromotedChangesOnly && includeLegacyIssues) {
			throw new TermServerScriptException("Cannot include legacy issues when only checking unpromoted changes");
		}
		super.init(run);
		getArchiveManager().setLoadOtherReferenceSets(true);
		//We need a new import each time incase the previous run did not load the other refsets
		//TODO We could set another flag to track if 'otherRefsets' are in memory or not
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
	}

	public void postInit() throws TermServerScriptException {
		super.postInit(REPORT_TAB_NAMES,
				new String[]{REPORT_TAB_ISSUES_COLUMNS, REPORT_TAB_SUMMARY_COLUMNS}
		);
	}

	public void runJob() throws TermServerScriptException {
		LOGGER.info("Running report \"{}\"", REPORT_NAME);

		var allConcepts = gl.getAllConcepts();
		LOGGER.info("Checking {} concepts...", allConcepts.size());
		sortedListOfConcepts = SnomedUtils.sort(allConcepts);
		LOGGER.info("Concepts sorted");

		firstPassRefsetsWithChanges();
		LOGGER.info("First pass complete");

		checkForMembersWithInvalidReferenceComponents();

		LOGGER.info("Checks complete, creating summary tag");
		populateSummaryTabAndTotal();

		LOGGER.info("Summary tab complete, all done.");
	}

	private void firstPassRefsetsWithChanges() throws TermServerScriptException {
		int conceptCount = 0;
		for (Concept concept : sortedListOfConcepts) {
			for (Component component : SnomedUtils.getAllComponents(concept)) {
				if (!(component instanceof RefsetMember)) {
					continue;
				}
				RefsetMember refsetMember = (RefsetMember) component;
				Concept refset = gl.getConcept(refsetMember.getRefsetId());
				//Have we seen this refset before?
				if (!refsetsWithChanges.containsKey(refset)) {
					refsetsWithChanges.put(refset, Boolean.FALSE);
				}
				if (!refsetsWithChanges.get(refset) &&
						StringUtils.isEmpty(refsetMember.getEffectiveTime())) {
					refsetsWithChanges.put(refset, Boolean.TRUE);
				}
				if (++conceptCount % 500000 == 0) {
					LOGGER.info("   ...checked {} concepts", conceptCount);
				}
			}
		}
	}

	private void checkForMembersWithInvalidReferenceComponents() throws TermServerScriptException {
		LOGGER.info("   Checking: {}", ISSUE_TITLE);
		initialiseSummary(ISSUE_TITLE);

		for (Concept concept : sortedListOfConcepts) {
			for (Component component : SnomedUtils.getAllComponents(concept)) {
				if (!(component instanceof RefsetMember) ||
						(!includeLegacyIssues && !isLegacySimple(component))
				) {
					continue;
				}

				//Are we considering a component belonging to this concept?  Or one of its descriptions?
				RefsetMember refsetMember = (RefsetMember) component;
				Concept refset = gl.getConcept(refsetMember.getRefsetId());
				if (REF_SETS_TO_IGNORE_FOR_INACTIVE_REFERENCE_CONPONENTS.contains(refset.getId())) {
					continue;
				}
				//Are we interested in this refset?
				if (includeLegacyIssues || refsetsWithChanges.get(refset)) {
					//Historical Associations and Inactivation Indicators are applied to inactive concepts
					//so we don't need to check those - we're expecting their referenced components to be inactive.
					if (refsetMember.getComponentType() != Component.ComponentType.HISTORICAL_ASSOCIATION
							&& refsetMember.getComponentType() != Component.ComponentType.ATTRIBUTE_VALUE
							&& refsetMember.isActive()) {
						String refsetStr = refset.toString();
						Component owningComponent = concept;
						if (!SnomedUtils.isConceptSctid(refsetMember.getReferencedComponentId())) {
							owningComponent = gl.getDescription(refsetMember.getReferencedComponentId());
						}

						if (owningComponent.isActive()) {
							continue;
						}

						summaryCounts.merge(refsetStr, 1, Integer::sum);
						reportAndIncrementSummary(concept,
								isLegacySimple(component),
								ISSUE_TITLE,
								refset,
								getLegacyIndicator(component),
								isActive(concept, component),
								component,
								component.getId(),
								owningComponent.getEffectiveTime());
					}
				}
			}
		}
	}

	protected void reportAndIncrementSummary(Concept c, boolean isLegacy, Object... details) throws TermServerScriptException {
		//Are we filtering this report to only concepts with unpromoted changes?
		if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c)) {
			return;
		}
		if (includeLegacyIssues || !isLegacy) {
			//First detail is the issue text
			incrementSummaryCount(details[0].toString());
			countIssue(c);
			report(PRIMARY_REPORT, c, details);
		}
	}

	public void populateSummaryTabAndTotal() {
		super.populateSummaryTabAndTotal(SECONDARY_REPORT);
		summaryCounts.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely(SECONDARY_REPORT, (Component) null, e.getKey(), e.getValue()));

		reportSafely(SECONDARY_REPORT, "");
		reportSafely(SECONDARY_REPORT, "");

		refsetsWithChanges.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely(SECONDARY_REPORT, (Component) null, e.getKey(), (e.getValue()? "Contains changes" : "Contains no changes")));

	}
}
