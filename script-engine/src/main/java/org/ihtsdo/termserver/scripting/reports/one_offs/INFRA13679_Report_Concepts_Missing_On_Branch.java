package org.ihtsdo.termserver.scripting.reports.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.stream.Collectors;


public class INFRA13679_Report_Concepts_Missing_On_Branch extends TermServerReport implements ReportClass {

	public static final String ECL = "<< 77465005 |Transplantation (procedure)| ";
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(INFRA13679_Report_Concepts_Missing_On_Branch.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		additionalReportColumns = "FSN, SemTag, Inactivation Reason, Historical Assocation(s), Association Target Effective Time(s), New transplant synonym added";
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Report Concepts Missing on Branch")
				.withDescription("This report lists concepts that are present in MAIN but not on the specified branch, along with details of the inactivations")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		//Recover concepts matching the ecl on _this_ branch
		Collection<Concept> branchConcepts = findConcepts(ECL);

		//Now run the same ECL against MAIN
		List<Concept> mainConcepts = new ArrayList<>(findConcepts("MAIN", ECL));

		mainConcepts.sort(Comparator.comparing(Concept::getSemTag).thenComparing(Concept::getFsn));
		for (Concept c : mainConcepts) {
			if (!branchConcepts.contains(c) && !c.isActiveSafely()) {
				reportMissingConcept(c);
			}
		}
	}

	private void reportMissingConcept(Concept c) throws TermServerScriptException {
		//Report our inactivation reason, then work out when the association target was created
		//and if it recently had a description added containing the words transplan
		String inactIndicatorStr = c.getInactivationIndicator() == null ? "not set": c.getInactivationIndicator().toString();
		String historicalAssociationsStr = SnomedUtils.prettyPrintHistoricalAssociations(c, getGraphLoader(), false);
		if (StringUtils.isEmpty(historicalAssociationsStr)) {
			historicalAssociationsStr = "no target(s)";
		}
		//Recover the historical association targets
		Set<Concept> targets = SnomedUtils.getHistoricalAssocationTargets(c, getGraphLoader());
		String associationETs = targets.stream().map(this::getEtOrNew).collect(Collectors.joining("\n"));
		String associationDescriptions = targets.stream()
				.flatMap(t -> t.getDescriptions(ActiveState.ACTIVE).stream())
				.filter(d -> d.getTerm().toLowerCase().contains("transplan"))
				.map(this::toStringWithET)
				.collect(Collectors.joining(", "));
		report(c, inactIndicatorStr, historicalAssociationsStr, associationETs, associationDescriptions);
	}

	private String getEtOrNew(Concept c) {
		String et = c.getEffectiveTime();
		return (StringUtils.isEmpty(et) ? "NEW":et);
	}

	private String toStringWithET(Description d) {
		String et = d.getEffectiveTime();
		return (StringUtils.isEmpty(et) ? "NEW":et) + " " + d;
	}

}
