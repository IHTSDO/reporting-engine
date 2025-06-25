package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DoseFormHelper;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoseFormValidation extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(DoseFormValidation.class);
	private static final String RECENT_CHANGES_ONLY = "Recent Changes Only";

	private List<Concept> allDrugs;
	private final Concept[] doseFormTypes = new Concept[] {HAS_MANUFACTURED_DOSE_FORM};
	private boolean isRecentlyTouchedConceptsOnly = false;
	private Set<Concept> recentlyTouchedConcepts;
	private DoseFormHelper doseFormHelper;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(DoseFormValidation.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3");  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"SCTID, FSN, Semtag, Issue, Details, Details, Details, Further Details",
				"Issue, Count"
		};
		String[] tabNames = new String[] {
				"Issues",
				"Summary"
		};
		allDrugs = SnomedUtils.sort(gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT));
		doseFormHelper = new DoseFormHelper();
		doseFormHelper.initialise(gl);
		
		super.postInit(tabNames, columnHeadings);
		
		if (jobRun.getParamBoolean(RECENT_CHANGES_ONLY)) {
			isRecentlyTouchedConceptsOnly = true;
			recentlyTouchedConcepts = SnomedUtils.getRecentlyTouchedConcepts(gl.getAllConcepts());
		}
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(RECENT_CHANGES_ONLY)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
			.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Dose Form Validation")
				.withDescription("This report checks CD and MPF Dose forms against a curated file of acceptable dose forms.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateDoseFormUsage();
		populateSummaryTab();
		LOGGER.info("Summary tab complete, all done.");
	}

	private void validateDoseFormUsage() throws TermServerScriptException {
		double conceptsConsidered = 0;
		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			
			DrugUtils.setConceptType(c);
			
			double percComplete = (conceptsConsidered++/allDrugs.size())*100;
			if (conceptsConsidered%4000==0) {
				LOGGER.info("Percentage Complete {}", (int)percComplete);
			}
			
			//DRUGS-784
			if (doseFormHelper.inScope(c)) {
				validateAcceptableDoseForm(c);
			}
		}
		LOGGER.info("Dose Form usage validation complete");
	}
	

	private void validateAcceptableDoseForm(Concept c) throws TermServerScriptException {
		String issueStr1 = c.getConceptType() + " uses unlisted dose form";
		String issueStr2 = c.getConceptType() + " uses unacceptable dose form";
		initialiseSummary(issueStr1);
		initialiseSummary(issueStr2);

		Concept thisDoseForm = SnomedUtils.getTarget(c, doseFormTypes, UNGROUPED, CharacteristicType.INFERRED_RELATIONSHIP);

		//Is this dose form acceptable?
		if (doseFormHelper.usesListedDoseForm(c, thisDoseForm)) {
			if (!doseFormHelper.usesAcceptableDoseForm(c, thisDoseForm)) {
				report(c, issueStr2, thisDoseForm);
			}
		} else {
			report(c, issueStr1, thisDoseForm);
		}
	}

	private void populateSummaryTab() {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.values().stream()
				.mapToInt(Integer::intValue).sum();
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
	}

	@Override
	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		//The first detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}

}
