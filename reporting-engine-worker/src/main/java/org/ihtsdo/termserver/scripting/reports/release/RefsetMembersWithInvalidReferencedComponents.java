package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.ReportClass;
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
 * <li>It needs to be a separate report called "Refset members with invalid referenced components".</li>
 * <li>It must present the list of refset members that have inactive referenced components.</li>
 * <li>The SAC needs to validate ONLY the derivative content that is being promoted at that time (using moduleID + refsetID fields) - NOT all other derivatives for that product.</li>
 * <li>This report should not be checking ANY derivative content if the only content being promoted is core module content.</li>
 * <li>e.g: if the task being promoted contains core module changes + changes to the GP/FP refset, then the validation should check ONLY the GP/FP refset and not all 20 odd other derivative refsets that form part of the International suite.</li>
 * <li>e.g: if Estonia are promoting changes to their "eesti Abo Veregrupi Klassifikaator Simple Refset", then the validation should check only that refset content, and not all 30+ other Estonian refsets.</li>
 * <li>For the International Authoring, this should be an Optional SAC. </li>
 * <li>For Managed Service Authoring, this should be a Mandatory SAC. </li>
 */
public class RefsetMembersWithInvalidReferencedComponents extends TermServerReport implements ReportClass {
	private static final Logger LOGGER = LoggerFactory.getLogger(RefsetMembersWithInvalidReferencedComponents.class);
	private static final String REPORT_NAME = "Refset members with invalid referenced components";
	private static final String REPORT_DESCRIPTION = "A report to check for both inactive and completely invalid SCTID's in refsets - the report can then distinguish whether each record that is failing has done so because it's inactive or invalid.";
	private static final String[] REPORT_TAB_NAMES = new String[]{"Issues", "Summary"};
	private static final String REPORT_TAB_ISSUES_COLUMNS = "SCTID, FSN, Semtag, Issue, Legacy, C/D/R Active, Detail, Additional Detail, Further Detail";
	private static final String REPORT_TAB_SUMMARY_COLUMNS = "Issue, Count";
	private static final String ISSUE_TITLE = "Active refset member for inactive concept";
	private static final String RELEASE_VALIDATION_FOLDER_ID = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ";
	private static final List<String> REF_SETS_TO_IGNORE_FOR_INACTIVE_REFERENCE_CONPONENTS = List.of("900000000000497000");
	private List<Concept> sortedListOfConcepts;

	/**
	 * Run the report from the command line, no arguments required for this report.
	 *
	 * @param args the command line arguments
	 * @throws TermServerScriptException if there is an error in the TermServerScript
	 * @throws IOException               if there is an error in the input/output
	 */
	public static void main(String[] args) throws TermServerScriptException, IOException {
		LOGGER.debug("Running from main CLI");
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		TermServerReport.run(RefsetMembersWithInvalidReferencedComponents.class, args, params);
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
				.withDefaultValue(false)
				.add(REPORT_OUTPUT_TYPES)
				.withType(JobParameter.Type.HIDDEN)
				.withDefaultValue(false)
				.add(REPORT_FORMAT_TYPE)
				.withType(JobParameter.Type.HIDDEN)
				.withDefaultValue(false)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName(REPORT_NAME)
				.withDescription(REPORT_DESCRIPTION)
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withParameters(jobParameters)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	public void init(JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = RELEASE_VALIDATION_FOLDER_ID;
		super.init(run);
		gl.setRecordPreviousState(true);
		getArchiveManager().setPopulateReleasedFlag(true);
		getArchiveManager().setLoadOtherReferenceSets(true);
	}

	public void postInit() throws TermServerScriptException {
		super.postInit(REPORT_TAB_NAMES,
				new String[]{REPORT_TAB_ISSUES_COLUMNS, REPORT_TAB_SUMMARY_COLUMNS},
				false);
	}

	public void runJob() throws TermServerScriptException {
		LOGGER.info("Running report \"{}\"", REPORT_NAME);

		LOGGER.info("Checking {} concepts...", gl.getAllConcepts().size());
		sortedListOfConcepts = SnomedUtils.sort(gl.getAllConcepts());

		checkForInactiveReferenceComponentId();

		LOGGER.info("Checks complete, creating summary tag");
		populateSummaryTabAndTotal();

		LOGGER.info("Summary tab complete, all done.");
	}

	private void checkForInactiveReferenceComponentId() throws TermServerScriptException {
		LOGGER.info("   Checking: {}", ISSUE_TITLE);
		initialiseSummary(ISSUE_TITLE);

		for (Concept concept : sortedListOfConcepts) {
			if (concept.isActive()) {
				continue;
			}

			var allComponents = SnomedUtils.getAllComponents(concept);

			for (Component component : allComponents) {
				if (!(component instanceof RefsetMember)) {
					continue;
				}
				RefsetMember refsetMember = (RefsetMember) component;

				if (refsetMember.getComponentType() != Component.ComponentType.HISTORICAL_ASSOCIATION
						&& refsetMember.getComponentType() != Component.ComponentType.ATTRIBUTE_VALUE
						&& refsetMember.getComponentType() != Component.ComponentType.LANGREFSET
						&& refsetMember.isActive()) {
					Concept refSet = gl.getConcept(refsetMember.getRefsetId());

					if (REF_SETS_TO_IGNORE_FOR_INACTIVE_REFERENCE_CONPONENTS.contains(refSet.getId())) {
						continue;
					}

					reportAndIncrementSummary(concept, isLegacySimple(component), ISSUE_TITLE, getLegacyIndicator(component), isActive(concept, component), component, component.getId());
				}
			}
		}
	}
}
