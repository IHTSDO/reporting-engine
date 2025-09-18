package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * FRI-254 A number of what were originally SQL queries now converted into a user-runnable
 * report
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreReleaseContentValidation extends HistoricDataUser implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(PreReleaseContentValidation.class);

	private static final String STD_HEADER = "SCTID, FSN, SemTag";

	private List<Concept> allActiveConceptsSorted;
	private List<Concept> allInactiveConceptsSorted;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();

		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20230630T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20230531T120000Z.zip");

		TermServerScript.run(PreReleaseContentValidation.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Pre-release Content Validation")
				.withDescription("A set of counts and informational queries originally run as SQL")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats

		getArchiveManager().setLoadDependencyPlusExtensionArchive(false);

		origProject = run.getProject();
		if (!StringUtils.isEmpty(run.getParamValue(THIS_RELEASE))) {
			run.setProject(run.getParamValue(THIS_RELEASE));
		}

		if (!StringUtils.isEmpty(run.getParamValue(MODULES))) {
			moduleFilter = Stream.of(run.getParamValue(MODULES).split(",", -1))
					.map(String::trim)
					.toList();
		}

		summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}

		String[] columnHeadings = new String[] {"Summary Item, Count",
				"SCTID, FSN, SemTag, New Hierarchy, Old Hierarchy",
				"SCTID, FSN, SemTag, Old FSN, Difference",
				STD_HEADER,
				STD_HEADER,
				"SCTID, FSN, SemTag, Defn Status Change",
				STD_HEADER,
				"SCTID, FSN, SemTag, Text Definition",
				STD_HEADER};
		String[] tabNames = new String[] {	"Summary Counts",
				"Hierarchy Switches",
				"FSN Changes",
				"Inactivated",
				"Reactivated",
				"DefnStatus",
				"New FSNs",
				"Text Defn",
				"ICD-O"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {

		allActiveConceptsSorted = gl.getAllConcepts().stream()
				.filter(Component::isActiveSafely)
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();

		allInactiveConceptsSorted =gl.getAllConcepts().stream()
				.filter(c -> !c.isActiveSafely())
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();

		LOGGER.info("Loading Previous Data");
		loadData(prevRelease);

		LOGGER.info("Reporting Top Level Hierarchy Switch");
		topLevelHierarchySwitch();

		LOGGER.info("Checking for fsn changes");
		checkForFsnChange();

		LOGGER.info("Checking for inactivated concepts");
		checkForInactivatedConcepts();

		LOGGER.info("Checking for reactivated concepts");
		checkForReactivatedConcepts();

		LOGGER.info("Checking for Definition Status Change");
		checkForDefinitionStatusChange();

		LOGGER.info("Checking for New FSNs");
		checkForNewFSNs();

		LOGGER.info("Check for New / Changed Text Definitions");
		checkForUpsertedTextDefinitions();

		LOGGER.info("Check for ICD-O changes");
		checkForICDOChanges();

		LOGGER.info("Compiling Summary Counts");
		compileSummaryCounts();
	}

	private void topLevelHierarchySwitch() throws TermServerScriptException {
		String summaryItem = "Top level hierarchy switch";
		initialiseSummaryInformation(summaryItem);
		for (Concept c : allActiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				HistoricData prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					Concept topLevel = SnomedUtils.getHierarchy(gl, c);
					if (!prevDatum.getHierarchy().equals(topLevel.getId())) {
						report(SECONDARY_REPORT, c, topLevel.toStringPref(), gl.getConcept(prevDatum.getHierarchy()).toStringPref());
						incrementSummaryInformation(summaryItem);
					}
				}
			} catch (Exception e) {
				report(SECONDARY_REPORT, c, "Error recovering hierarchy: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForFsnChange() throws TermServerScriptException {
		String summaryItem = "FSN Changes";
		initialiseSummaryInformation(summaryItem);
		for (Concept c : allActiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				HistoricData prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					if (!prevDatum.getFsn().equals(c.getFsn())) {
						report(TERTIARY_REPORT, c, prevDatum.getFsn(), StringUtils.difference(c.getFsn(), prevDatum.getFsn()));
						incrementSummaryInformation(summaryItem);
					}
				}
			} catch (Exception e) {
				report(TERTIARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForInactivatedConcepts() throws TermServerScriptException {
		String summaryItem = "Inactivated Concepts";
		initialiseSummaryInformation(summaryItem);
		for (Concept c : allInactiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				HistoricData prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					if (prevDatum.isActive()) {
						report(QUATERNARY_REPORT, c);
						incrementSummaryInformation(summaryItem);
					}
				}
			} catch (Exception e) {
				report(QUATERNARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForReactivatedConcepts() throws TermServerScriptException {
		String summaryItem = "Reactivated Concepts";
		initialiseSummaryInformation(summaryItem);
		for (Concept c : allActiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				HistoricData prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					if (!prevDatum.isActive()) {
						report(QUINARY_REPORT, c);
						incrementSummaryInformation(summaryItem);
					}
				}
			} catch (Exception e) {
				report(QUINARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForDefinitionStatusChange() throws TermServerScriptException {
		String summaryItem1 = "Definition Status Change SD->P";
		initialiseSummaryInformation(summaryItem1);
		String summaryItem2 = "Definition Status Change P->SD";
		initialiseSummaryInformation(summaryItem2);

		for (Concept c : allActiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				HistoricData prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					//If what was SD is now P, or visa versa, report
					if (prevDatum.isSD() == c.isPrimitive()) {
						report(SENARY_REPORT, c, c.isPrimitive()?"SD->P":"P->SD");
						incrementSummaryInformation(c.isPrimitive()?summaryItem1:summaryItem2);
					}
				}
			} catch (Exception e) {
				report(SENARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForNewFSNs() throws TermServerScriptException {
		String summaryItem = "New FSNs";
		initialiseSummaryInformation(summaryItem);
		for (Concept c : allActiveConceptsSorted) {
			try {
				Description fsn = c.getFSNDescription();
				//Was this concept in the previous release and if so, has it switched?
				HistoricData prevDatum = prevData.get(c.getId());
				if (prevDatum == null || !prevDatum.getDescIds().contains(fsn.getId())) {
					report(SEPTENARY_REPORT, c);
					incrementSummaryInformation(summaryItem);
				}
			} catch (Exception e) {
				report(SENARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForUpsertedTextDefinitions() throws TermServerScriptException {
		String summaryItem = "Text Definitions new / changed";
		initialiseSummaryInformation(summaryItem);
		for (Concept c : allActiveConceptsSorted) {
			try {
				for (Description textDefn : c.getDescriptions(null, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE)) {
					//Is this text definition in the delta?  Check null or current effective time
					if (StringUtils.isEmpty(textDefn.getEffectiveTime()) ||
							(thisEffectiveTime != null && textDefn.getEffectiveTime().equals(thisEffectiveTime))) {
						report(OCTONARY_REPORT, c, textDefn);
						incrementSummaryInformation(summaryItem);
					}
				}

			} catch (Exception e) {
				report(OCTONARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

	private void checkForICDOChanges() throws TermServerScriptException {
		report(NONARY_REPORT, "ICDO Refset Members not available");
	}

	private void compileSummaryCounts() {
		String msg = "Active concepts with active historical associations";
		initialiseSummaryInformation(msg);
		for (Concept c : allActiveConceptsSorted) {
			if (!c.getAssociationEntries(ActiveState.ACTIVE, true).isEmpty()) {
				incrementSummaryInformation(currentPositionProjectKey);
			}
		}

		msg = "Active concepts with active inactivation reason";
		initialiseSummaryInformation(msg);
		for (Concept c : allActiveConceptsSorted) {
			if (!c.getInactivationIndicatorEntries(ActiveState.ACTIVE).isEmpty()) {
				incrementSummaryInformation(currentPositionProjectKey);
			}
		}
	}
}