package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists active members of simple-type reference sets where the referenced concept is inactive.
 * Unlike {@link InactiveConceptInRefset} (which is restricted to concepts inactivated in the
 * current authoring cycle), this report includes inactive concepts regardless of when they
 * were retired — useful for refset maintainers auditing outdated memberships.
 *
 * <p>Default refset scope: descendants of {@code 446609009 |Simple type reference set|}.
 * An optional {@code ECL} parameter overrides the default, allowing a narrower scope
 * (e.g. a single refset ID, or a curated OR-list).</p>
 *
 * <p>Refset memberships come from the in-memory snapshot, which loads all reference sets
 * for this report, so the terminology server is only asked to resolve the refset ECL.</p>
 */
public class RefsetMaintenanceReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(RefsetMaintenanceReport.class);

	private static final String DEFAULT_REFSET_ECL = "<446609009 |Simple type reference set|";
	private static final String OUTDATED_MEMBERSHIPS = "Outdated memberships per refset";
	private static final String NOT_APPLICABLE = "N/A";
	private static final String INCLUDE_INT_REFSETS = "Include International Refsets";

	private boolean includeIntRefsets;

	private String userECL;
	private Map<String, Concept> targetRefsetsById;

	public static void main(String[] args) throws TermServerScriptException {
		new RefsetMaintenanceReport().standardExecution(args, new ExecutionOptions().withImportAllRefsets());
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		userECL = run.getParamValue(ECL);
		includeIntRefsets = run.getMandatoryParamBoolean(INCLUDE_INT_REFSETS);
		super.init(run);
		getSnapshotConfiguration().setLoadOtherReferenceSets(true);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String ecl = StringUtils.isEmpty(userECL) ? DEFAULT_REFSET_ECL : userECL;
		LOGGER.info("Resolving refsets from ECL: {}", ecl);
		targetRefsetsById = findConcepts(ecl).stream()
				.collect(Collectors.toMap(Concept::getId, c -> c, (a, b) -> a));
		if (!includeIntRefsets) {
			int refsetCount = targetRefsetsById.size();
			targetRefsetsById.values().removeIf(r -> !SnomedUtils.hasExtensionSCTID(r));
			incrementSummaryCount("Refsets", "International refsets excluded", refsetCount - targetRefsetsById.size());
		}
		LOGGER.info("Found {} reference set(s) in scope", targetRefsetsById.size());

		summaryTabIdx = PRIMARY_REPORT;
		String[] columnHeadings = new String[] {
				"Category, Item, Count, , , ",
				"Refset Id, Refset FSN, Concept Id, Concept FSN, SemTag, Reason, Assoc Type, Replacement Id, Replacement FSN"
		};
		String[] tabNames = new String[] { "Summary", "Outdated Memberships" };
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
				.add(INCLUDE_INT_REFSETS).withType(Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Refset Maintenance Report")
				.withDescription("Lists active members of simple-type reference sets whose referenced " +
						"concept is inactive, with the inactivation reason and suggested replacement " +
						"(historical association). Unlike 'Inactivated Concepts in Refsets', this report " +
						"includes inactive concepts from any release cycle, not just the current one. " +
						"An optional ECL parameter filters which refsets are included (default: " +
						"all simple-type reference sets).")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> inactiveConcepts = gl.getAllConcepts().stream()
				.filter(c -> !c.isActiveSafely())
				.toList();
		incrementSummaryCount("Concepts", "Inactive concepts checked", inactiveConcepts.size());
		incrementSummaryCount("Refsets", "Refsets in scope", targetRefsetsById.size());

		List<RefsetMember> outdatedMembers = new ArrayList<>();
		for (Concept c : inactiveConcepts) {
			for (RefsetMember m : c.getOtherRefsetMembers()) {
				if (m.isActiveSafely() && targetRefsetsById.containsKey(m.getRefsetId())) {
					outdatedMembers.add(m);
				}
			}
		}
		LOGGER.info("{} active memberships of {} inactive concepts found in {} refsets",
				outdatedMembers.size(), inactiveConcepts.size(), targetRefsetsById.size());

		outdatedMembers.sort(Comparator.comparing(RefsetMember::getRefsetId)
				.thenComparing(RefsetMember::getReferencedComponentId));
		for (RefsetMember m : outdatedMembers) {
			reportMember(m, gl.getConcept(m.getReferencedComponentId()));
		}
		reportSummaryCounts(summaryTabIdx);
	}

	private void reportMember(RefsetMember m, Concept concept) throws TermServerScriptException {
		Concept refset = targetRefsetsById.get(m.getRefsetId());
		incrementSummaryCount(OUTDATED_MEMBERSHIPS, refset.toString());
		countIssue(concept);
		InactivationIndicator reason = concept.getInactivationIndicator();
		List<AssociationEntry> assocs = concept.getAssociationEntries(ActiveState.ACTIVE, true);

		if (assocs.isEmpty()) {
			report(SECONDARY_REPORT,
					refset.getId(), refset.getFsn(),
					concept.getId(), concept.getFsn(), concept.getSemTag(),
					reason, NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE);
		} else {
			for (AssociationEntry a : assocs) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept target = gl.getConcept(a.getTargetComponentId());
				report(SECONDARY_REPORT,
						refset.getId(), refset.getFsn(),
						concept.getId(), concept.getFsn(), concept.getSemTag(),
						reason, assocType,
						target == null ? "" : target.getId(),
						target == null ? "" : target.getFsn());
			}
		}
	}
}
