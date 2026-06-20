package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.scheduler.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaccineValidation extends DrugsReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(VaccineValidation.class);

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "false");
		TermServerScript.run(VaccineValidation.class, args, params);
	}

	@Override
	public Job getJob() {
		return getDrugJob("Vaccine Validation",
				"This report checks for a number of potential inconsistencies in the Vaccine Hierarchy");
	}

	@Override
	public void runJob() throws TermServerScriptException {
		mode = Mode.VACCINE;
		DrugsModelingAndTerming drugsModelingAndTerming = new DrugsModelingAndTerming();
		drugsModelingAndTerming.linkReport(this);
		drugsModelingAndTerming.validateDrugsModeling();
		drugsModelingAndTerming.validateTherapeuticRole();

		reportSummaryCounts(SECONDARY_REPORT, SUMMARY_SORT_ORDER.COUNT);
		LOGGER.info("Summary tab complete, all done.");
	}

}
