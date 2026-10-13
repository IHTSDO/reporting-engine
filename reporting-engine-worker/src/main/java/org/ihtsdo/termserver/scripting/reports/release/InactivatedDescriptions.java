package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.stream.Collectors;

public class InactivatedDescriptions extends TermServerReport implements ReportClass {

	static final String SEMTAG_FILTER_PARAM = "Filter for SemTag";
	static final String REFSET_IDS = "List of reference set IDs";

	private String semtagFilter;
	private String refsetECL;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(REFSET_IDS, "231621000210105, 900000000000508004, 900000000000509007");
		TermServerScript.run(InactivatedDescriptions.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		semtagFilter = run.getParamValue(SEMTAG_FILTER_PARAM);

		if (!StringUtils.isEmpty(run.getParamValue(REFSET_IDS))) {
			refsetECL = Arrays.stream(run.getParamValue(REFSET_IDS).split(","))
					.map(String::trim)
					.collect(Collectors.joining(" OR "));
		}
		//"Unpromoted changes only" is picked up by the TermServerReport parent class as part of init
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, ModuleId, Reason, Assoc Type, Assoc Value",
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, ModuleId, RefsetId, Refset ModuleId, Reason, Assoc Type, Assoc Value"
		};
		String[] tabNames = new String[] {	
				"Descriptions Inactivated",
				"Descriptions Inactivated in Refsets"
		};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SEMTAG_FILTER_PARAM).withType(Type.STRING)
				.add(REFSET_IDS).withType(Type.STRING)
				.add(UNPROMOTED_CHANGES_ONLY).withType(Type.BOOLEAN).withMandatory().withDefaultValue("false")
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Descriptions")
				.withDescription("This report lists all descriptions inactivated in the current release cycle " +
								"along with the reason and historical association where applicable, " +
								"optionally restricted to a particular semantic tag. ")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Description> inactiveDescriptionsSegment = new ArrayList<>();
		for (Concept c : scopeAndSort(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
				if (inScope(d)) {
					if (inactiveDescriptionsSegment.size() >= 100) {
						reportInactiveDescriptionsInRefsets(inactiveDescriptionsSegment);
						inactiveDescriptionsSegment.clear();
					}
					if (!StringUtils.isEmpty(refsetECL)) {
						inactiveDescriptionsSegment.add(d);
					}

					reportInactiveDescription(c, d);
					countIssue(c);
				}
			}
		}
		if (!inactiveDescriptionsSegment.isEmpty()) {
			reportInactiveDescriptionsInRefsets(inactiveDescriptionsSegment);
		}
	}

	private void reportInactiveDescription(Concept c, Description d) throws TermServerScriptException {
		boolean reported = false;
		InactivationIndicator i = d.getInactivationIndicator();
		for (AssociationEntry a : d.getAssociationEntries(ActiveState.ACTIVE, true)) {
			String assocType = SnomedUtils.getAssociationType(a);
			Concept assocValue = gl.getConcept(a.getTargetComponentId());
			report(c, d.getLang(), d.getDescriptionId(), d.getTerm(), d.getModuleId(), i, assocType, assocValue);
			reported = true;
		}
		if (!reported) {
			report(c, d.getLang(), d.getDescriptionId(), d.getTerm(), d.getModuleId(), i, "N/A", "N/A");
		}
	}

	private void reportInactiveDescriptionsInRefsets(List<Description> inactiveDescriptionsSegment) throws TermServerScriptException {
		List<String> referencedComponentIds = inactiveDescriptionsSegment.stream()
				.map(Description::getDescriptionId)
				.toList();
		
		Collection<RefsetMember> members = searchMembers(referencedComponentIds, refsetECL);

		for (RefsetMember m : members) {
			Concept refset = gl.getConcept(m.getRefsetId());
			Description d = gl.getDescription(m.getReferencedComponentId());
			Concept c = gl.getConcept(d.getConceptId());

			boolean reported = false;
			InactivationIndicator i = d.getInactivationIndicator();
			for (AssociationEntry a : d.getAssociationEntries(ActiveState.ACTIVE, true)) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept assocValue = gl.getConcept(a.getTargetComponentId());
				report(SECONDARY_REPORT, c, d.getLang(), d.getDescriptionId(), d.getTerm(), d.getModuleId(), refset, refset.getModuleId(), i, assocType, assocValue);
				reported = true;
			}
			if (!reported) {
				report(SECONDARY_REPORT, c, d.getLang(), d.getDescriptionId(), d.getTerm(), d.getModuleId(), refset, refset.getModuleId(), i, "N/A", "N/A");
			}
		}
	}

	private boolean inScope(Description d) {
		// We want inactive descriptions modified in the current release cycle i.e. effectiveTime is null
		// Also optionally, unpromoted changes only
		return !d.isActiveSafely() && StringUtils.isEmpty(d.getEffectiveTime())
				&& (!unpromotedChangesOnly || unpromotedChangesHelper.hasUnpromotedChange(d));
	}
	
	private boolean hasRequiredSemTag(Concept c) {
		// If we didn't specify a filter, then it's in
		if (StringUtils.isEmpty(semtagFilter)) {
			return true;
		}
		
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn(), true)[1];
		return semTag != null && semTag.contains(semtagFilter);
	}

	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		// Filter concepts by a specific semantic tag (if provided) and then sort
		return superSet.stream()
				.filter(this::hasRequiredSemTag)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();
	}
	
}
