package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerScriptException;
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

public class InactivatedDescriptions extends TermServerReport implements ReportClass {

	static final String SEMTAG_FILTER_PARAM = "Filter for SemTag";
	private String semtagFilter;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(InactivatedDescriptions.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		semtagFilter = run.getParamValue(SEMTAG_FILTER_PARAM);
		//Unpromoted changes only is picked up by the TermServerReport parent class as part of init
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"ConceptId, FSN, SemTag, Lang, DescriptionId, Term, Reason, Assoc Type, Assoc Value"
		};
		String[] tabNames = new String[] {	
				"Descriptions Inactivated"
		};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SEMTAG_FILTER_PARAM).withType(Type.STRING)
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
		for (Concept c : scopeAndSort(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
				if (inScope(d)) {
					boolean reported = false;
					InactivationIndicator i = d.getInactivationIndicator();
					for (AssociationEntry a : d.getAssociationEntries(ActiveState.ACTIVE, true)) {
						String assocType = SnomedUtils.getAssociationType(a);
						Concept assocValue = gl.getConcept(a.getTargetComponentId());
						report(c, d.getLang(), d.getDescriptionId(), d.getTerm(), i, assocType, assocValue);
						reported = true;
					}
					if (!reported) {
						report(c, d.getLang(), d.getDescriptionId(), d.getTerm(), i, "N/A");
					}
					countIssue(c);
				}
			}
		}
	}
	
	private boolean inScope(Description d) {
		// We want inactive descriptions modified  in the current release cycle i.e. effectiveTime is null
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
