package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
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
 * <p>Refset memberships are read directly from Snowstorm via per-refset GET queries,
 * not from {@code Concept.getOtherRefsetMembers()} — the locally-loaded refset graph
 * frequently misses members for refsets whose files weren't bundled in the loaded
 * archive. Snowstorm gives us the authoritative branch state.</p>
 */
public class RefsetMaintenanceReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(RefsetMaintenanceReport.class);

	private static final long REFSET_PAUSE_MS = 200L;

	private static final String DEFAULT_REFSET_ECL = "<446609009 |Simple type reference set|";

	private String userECL;
	private Set<String> targetRefsetIds;
	// Cache of refset-id → Concept resolved from Snowstorm, used to fill the Refset FSN
	// column when the locally-loaded refset concept is a stub.
	private Map<String, Concept> targetRefsetsById;

	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(RefsetMaintenanceReport.class, args, new HashMap<>());
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		getArchiveManager().setLoadOtherReferenceSets(true);
		userECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String ecl = StringUtils.isEmpty(userECL) ? DEFAULT_REFSET_ECL : userECL;
		LOGGER.info("Resolving refsets from ECL: {}", ecl);
		Collection<Concept> refsets = findConcepts(ecl);
		targetRefsetsById = refsets.stream()
				.collect(Collectors.toMap(Concept::getId, c -> c, (a, b) -> a));
		targetRefsetIds = targetRefsetsById.keySet();
		LOGGER.info("Found {} reference set(s) in scope", targetRefsetIds.size());

		String[] columnHeadings = new String[] {
				"Refset Id, Refset FSN, Concept Id, Concept FSN, SemTag, Reason, Assoc Type, Replacement Id, Replacement FSN"
		};
		String[] tabNames = new String[] { "Outdated Memberships" };
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
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
		// Set of inactive concept IDs for fast membership checks. isActiveSafely() treats
		// null-active as false, so "stub" concepts (referenced by indicator/association
		// files but not by any concept-file row) are included — they're genuinely
		// inactive from prior release cycles.
		Set<String> inactiveConceptIds = gl.getAllConcepts().stream()
				.filter(c -> !c.isActiveSafely())
				.map(Concept::getConceptId)
				.collect(Collectors.toSet());

		String branchPath = project.getBranchPath();
		LOGGER.info("Querying Snowstorm for members of {} refsets (filtering {} inactive concepts client-side)",
				targetRefsetIds.size(), inactiveConceptIds.size());

		// Iterate refsets one at a time using GET /members?referenceSet=<id>. This avoids
		// POST /members/search (unsupported by the authoring-proxy URL) and keeps each
		// request short. tsClient.findRefsetMembers handles pagination internally.
		int outdatedMembershipsFound = 0;
		int refsetNum = 0;
		for (String refsetId : targetRefsetIds) {
			refsetNum++;
			Collection<RefsetMember> members = tsClient.findRefsetMembers(branchPath, refsetId, null);
			int matchesThisRefset = 0;
			for (RefsetMember m : members) {
				if (!m.isActiveSafely()) {
					continue;
				}
				if (!inactiveConceptIds.contains(m.getReferencedComponentId())) {
					continue;
				}
				Concept concept = gl.getConcept(m.getReferencedComponentId(), true, false);
				reportMember(m, concept);
				outdatedMembershipsFound++;
				matchesThisRefset++;
			}
			if (matchesThisRefset > 0 || refsetNum % 20 == 0) {
				LOGGER.info("[{}/{}] Refset {}: {} members, {} on inactive concepts (running total: {})",
						refsetNum, targetRefsetIds.size(), refsetId, members.size(),
						matchesThisRefset, outdatedMembershipsFound);
			}
			try {
				Thread.sleep(REFSET_PAUSE_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		LOGGER.info("Done — {} outdated memberships found across {} refsets",
				outdatedMembershipsFound, targetRefsetIds.size());
	}

	private void reportMember(RefsetMember m, Concept concept) throws TermServerScriptException {
		// Prefer the Snowstorm-resolved refset concept (has FSN populated as a string)
		// over the locally-loaded one, which may be a stub for some extension refsets.
		Concept refset = targetRefsetsById.get(m.getRefsetId());
		if (refset == null) {
			refset = gl.getConcept(m.getRefsetId());
		}
		InactivationIndicator reason = concept.getInactivationIndicator();

		List<AssociationEntry> assocs = concept.getAssociationEntries(ActiveState.ACTIVE, true);

		if (assocs.isEmpty()) {
			report(PRIMARY_REPORT,
					refset.getId(), refset.getFsn(),
					concept.getId(), concept.getFsn(), concept.getSemTag(),
					reason, "N/A", "N/A", "N/A");
			countIssue(concept);
		} else {
			for (AssociationEntry a : assocs) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept target = gl.getConcept(a.getTargetComponentId());
				report(PRIMARY_REPORT,
						refset.getId(), refset.getFsn(),
						concept.getId(), concept.getFsn(), concept.getSemTag(),
						reason, assocType,
						target == null ? "" : target.getId(),
						target == null ? "" : target.getFsn());
				countIssue(concept);
			}
		}
	}
}
