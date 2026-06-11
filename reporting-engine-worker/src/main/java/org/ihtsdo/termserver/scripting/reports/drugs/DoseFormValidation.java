package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoseFormValidation extends DrugsReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(DoseFormValidation.class);

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(DoseFormValidation.class, args, params);
	}

	@Override
	public Job getJob() {
		return getDrugJob("Dose Form Validation",
				   "This report checks CD and MPF Dose forms against a curated file of acceptable dose forms.");
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateDoseFormUsage();
		populateSummaryTab();
		LOGGER.info("Summary tab complete, all done.");
	}

	public void validateDoseFormUsage() throws TermServerScriptException {
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


}
