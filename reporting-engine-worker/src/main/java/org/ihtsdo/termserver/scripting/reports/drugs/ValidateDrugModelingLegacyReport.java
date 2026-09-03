package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateDrugModelingLegacyReport extends DrugsReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidateDrugModelingLegacyReport.class);

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(ValidateDrugModelingLegacyReport.class, args, params);
	}

	@Override
	public Job getJob() {
		return getDrugJob("Legacy Drugs Validation Report",
				"This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy.");
	}

	@Override
	public void runJob() throws TermServerScriptException {
		runAllDrugValidations();
		reportSummaryCounts(SECONDARY_REPORT, SUMMARY_SORT_ORDER.COUNT);
		LOGGER.info("Summary tab complete, all done.");
	}

	private void runAllDrugValidations() throws TermServerScriptException {

		BoSSPAICheck bossPAICheck = new BoSSPAICheck();
		bossPAICheck.linkReport(this);
		bossPAICheck.validateBoSSPai();

		DrugsModelingAndTerming modelingAndTerming = new DrugsModelingAndTerming();
		modelingAndTerming.linkReport(this);
		modelingAndTerming.validateDrugsModeling();
		modelingAndTerming.validateTherapeuticRole();

		DoseFormValidation doseFormValidation = new DoseFormValidation();
		doseFormValidation.linkReport(this);
		doseFormValidation.validateDoseFormUsage();

		MpMpfValidation mpMpMpfValidation = new MpMpfValidation();
		mpMpMpfValidation.linkReport(this);
		mpMpMpfValidation.validateMpMpfModeling();

		LOGGER.info("Drugs validation complete");
	}


	@Override
	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		incrementSummaryCount(ISSUE_COUNTS, details[0].toString());
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}

}
