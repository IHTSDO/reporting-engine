package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.HighVolumeUsageHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AtomicLongMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RP-370 List concepts being inactivated in this release which also appear in known refsets
 */
public class InactiveConceptInRefset extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactiveConceptInRefset.class);

	public static final String REFSET_ECL = "(< 446609009 |Simple type reference set| OR < 900000000000496009 |Simple map type reference set|) MINUS 900000000000497000 |CTV3 simple map reference set (foundation metadata concept)|";
	public static final String FALSE = "false";
	private Collection<Concept> referenceSets;
	private List<Concept> emptyReferenceSets;
	private List<Concept> outOfScopeReferenceSets;
	private AtomicLongMap<Concept> refsetSummary = AtomicLongMap.create();
	private AtomicLongMap<Concept> moduleSummary = AtomicLongMap.create();
	private static final String INCLUDE_LAST_RELEASE = "Include latest Int release";
	private String lastReleaseEffectiveTime = null;
	private static final String EXT_REF_ONLY = "Extension Refsets Only";
	private boolean extensionRefsetOnly = false;
	public static final int CLAUSE_LIMIT = 100;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_LAST_RELEASE, FALSE);
		params.put(EXT_REF_ONLY, FALSE);
		TermServerScript.run(InactiveConceptInRefset.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		super.init(run);

		//Doesn't make sense to include the last release if we're also working with unpromoted changes
		if (unpromotedChangesOnly && getJobRun().getParameters().getMandatoryBoolean(INCLUDE_LAST_RELEASE)) {
			throw new TermServerScriptException("Cannot include last release when also working with unpromoted changes");
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		if (getJobRun().getParameters().getMandatoryBoolean(INCLUDE_LAST_RELEASE)) {
			lastReleaseEffectiveTime = project.getMetadata().getDependencyRelease();
		}
		extensionRefsetOnly = getJobRun().getParameters().getMandatoryBoolean(EXT_REF_ONLY);
		
		referenceSets = findConcepts(REFSET_ECL);
		removeEmptyAndNoScopeRefsets();
		LOGGER.info("Recovered {} simple reference sets and maps", referenceSets.size());

		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Count",
				"Id, FSN, SemTag, Count, Refset Module",
				"Id, FSN, SemTag, Module, Refset, Refset Module, Reason, Assoc Type, Assoc Value",
				"Id, FSN, SemTag, Module, Usage, Reason, Assoc Type, Assoc Value"
		};
		String[] tabNames = new String[] {
				"Module Summary",
				"Per Refset Counts",
				"Inactivated Concepts in Refsets",
				"Inactivated UK High Volume Usage Concepts"
		};
		super.postInit(tabNames, columnHeadings);
	}

	private void removeEmptyAndNoScopeRefsets() throws TermServerScriptException {
		emptyReferenceSets = new ArrayList<>();
		outOfScopeReferenceSets = new ArrayList<>();
		for (Concept refset : referenceSets) {
			if (extensionRefsetOnly && !inScope(refset)) {
				outOfScopeReferenceSets.add(refset);
				continue;
			}
			if (getConceptsCount("^" + refset) == 0) {
				emptyReferenceSets.add(refset);
			} else {
				refsetSummary.put(refset, 0);
			}
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		referenceSets.removeAll(emptyReferenceSets);
		referenceSets.removeAll(outOfScopeReferenceSets);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_LAST_RELEASE).withType(Type.BOOLEAN).withMandatory().withDefaultValue(FALSE)
				.add(EXT_REF_ONLY).withType(Type.BOOLEAN).withMandatory().withDefaultValue(FALSE)
				.add(UNPROMOTED_CHANGES_ONLY).withType(Type.BOOLEAN).withMandatory().withDefaultValue(FALSE)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Concepts in Refsets")
				.withDescription("This report lists concepts inactivated in the current authoring cycle" + 
				" which are members of a published reference set, or in a list of high usage concepts." +
				" Warning: because this report involves inactive concepts, it cannot use ECL and therefore takes ~40 minutes to run.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.withExpectedDuration(40)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> inactivatedConcepts = getInactivatedConcepts();

		if (!extensionRefsetOnly) {
			LOGGER.debug("Checking {} inactivated concepts against High Usage SCTIDs", inactivatedConcepts.size());
			List<String> inactivatedConceptIds = inactivatedConcepts.stream().
					map(Concept::getId)
					.toList();
			checkHighVolumeUsage(inactivatedConceptIds);
		}
		
		LOGGER.debug("Checking {} inactivated concepts against {} refsets", inactivatedConcepts.size(), referenceSets.size());
		String viableRefsetECL = referenceSets.stream()
				.map(Concept::getId)
				.collect(Collectors.joining(" OR "));
		
		int count = 0;
		for (List<Concept> inactiveConceptsSegment : Iterables.partition(inactivatedConcepts, CLAUSE_LIMIT)) {
			Collection<RefsetMember> members = findRefsetMembers(inactiveConceptsSegment, viableRefsetECL);
			for (RefsetMember m : members) {
				Concept refset = gl.getConcept(m.getRefsetId());
				Concept module = gl.getConcept(refset.getModuleId());
				Concept c = gl.getConcept(m.getReferencedComponentId());
				
				//Ensure we initialise the module summary to capture 0 counts
				if (!moduleSummary.containsKey(module)) {
					moduleSummary.put(module, 0);
				}
				
				if (referenceSets.contains(refset)) {
					outputConcept(c, refset);
					refsetSummary.getAndIncrement(refset);
					moduleSummary.getAndIncrement(module);
					countIssue(c);
				}
			}
			try {
				Thread.sleep(200L);
			} catch (Exception e) {
				Thread.currentThread().interrupt();
			}
			
			count += inactiveConceptsSegment.size();
			LOGGER.debug("Checked {} inactive concepts", count);
		}

		outputSummaryCounts();
	}

	private void outputConcept(Concept c, Concept refset) throws TermServerScriptException {
		boolean reported = false;
		InactivationIndicator i = c.getInactivationIndicator();
		for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
			String assocType = SnomedUtils.getAssociationType(a);
			Concept assocValue = gl.getConcept(a.getTargetComponentId());
			report(TERTIARY_REPORT, c, c.getModuleId(), refset, refset.getModuleId(), i, assocType, assocValue);
			reported = true;
		}
		if (!reported) {
			report(TERTIARY_REPORT, c, c.getModuleId(), refset, refset.getModuleId(), i, "N/A", "N/A");
		}
	}

	private void outputConcept(Concept c, HighVolumeUsageHelper.Usage usage) throws TermServerScriptException {
		boolean reported = false;
		InactivationIndicator i = c.getInactivationIndicator();
		for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
			String assocType = SnomedUtils.getAssociationType(a);
			Concept assocValue = gl.getConcept(a.getTargetComponentId());
			report(QUATERNARY_REPORT, c, c.getModuleId(), usage, i, assocType, assocValue);
			reported = true;
		}
		if (!reported) {
			report(QUATERNARY_REPORT, c, c.getModuleId(), usage, i, "N/A", "N/A");
		}
	}

	private void outputSummaryCounts() throws TermServerScriptException {
		for (Map.Entry<Concept, Long> entry : moduleSummary.asMap().entrySet()) {
			Concept module = entry.getKey();
			report(PRIMARY_REPORT, module, entry.getValue());
		}

		for (Map.Entry<Concept, Long> entry : refsetSummary.asMap().entrySet()) {
			Concept refset = entry.getKey();
			report(SECONDARY_REPORT, refset, entry.getValue(), refset.getModuleId());
		}

		for (Concept emptyRefset : emptyReferenceSets) {
			report(SECONDARY_REPORT, emptyRefset, " not populated in project: " + getProject().getKey(), emptyRefset.getModuleId());
		}

		for (Concept outOfScopeReferenceSet : outOfScopeReferenceSets) {
			report(SECONDARY_REPORT, outOfScopeReferenceSet, " out of scope in project: " + getProject().getKey(), outOfScopeReferenceSet.getModuleId());
		}
	}

	private List<Concept> getInactivatedConcepts() {
		//If we are including the last release then all concepts are in scope and
		//inactivations from the previous international release are included
		if (lastReleaseEffectiveTime == null) {
			//We're looking for concepts which are inactive and have no effective time
			return gl.getAllConcepts().stream()
					.filter(c -> !c.isActiveSafely())
					.filter(this::inScope)
					.filter(c -> (!unpromotedChangesOnly || unpromotedChangesHelper.hasUnpromotedChange(c)))
					.filter(c -> StringUtils.isEmpty(c.getEffectiveTime()))
					.toList();
		} else {
			return gl.getAllConcepts().stream()
					.filter(c -> !c.isActiveSafely())
					.filter(c -> (StringUtils.isEmpty(c.getEffectiveTime()) || c.getEffectiveTime().equals(lastReleaseEffectiveTime)))
					.toList();
		}
	}

	private void checkHighVolumeUsage(List<String> inactivatedIds) throws TermServerScriptException {
		Concept hvu = new Concept("0","High Volume Usage (UK)");
		hvu.setModuleId(SCTID_CORE_MODULE);
		HighVolumeUsageHelper hvuHelper = new HighVolumeUsageHelper();
		for (String sctid : inactivatedIds) {
			if (hvuHelper.hasRecentHighUsage(sctid)) {
				Concept c =  gl.getConcept(sctid);
				outputConcept(c, hvuHelper.getUsage(sctid));
				refsetSummary.getAndIncrement(hvu);
			}
		}
	}

}
