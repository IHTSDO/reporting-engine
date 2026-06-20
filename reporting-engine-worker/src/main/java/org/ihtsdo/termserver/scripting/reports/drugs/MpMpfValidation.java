package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.snomed.otf.scheduler.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MpMpfValidation extends DrugsReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MpMpfValidation.class);

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(MpMpfValidation.class, args, params);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Expected Result, Variance, Source, Further Details",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		super.postInit(tabNames, columnHeadings);

	}

	@Override
	public Job getJob() {
		return getDrugJob("MP MPF Validation",
				           "This report checks for a number of potential issues with MP/MPF concepts, as per RP-740.");
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateMpMpfModeling();
		reportSummaryCounts(SECONDARY_REPORT, SUMMARY_SORT_ORDER.COUNT);
		LOGGER.info("Summary tab complete, all done.");
	}

	public void validateMpMpfModeling() throws TermServerScriptException {
		double conceptsConsidered = 0;

		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			
			if (c.getFsn().toLowerCase().contains("vaccine")) {
				continue;
			}
			
			DrugUtils.setConceptType(c);
			
			if (isCD(c)) {
				continue;
			}
			
			double percComplete = (conceptsConsidered++/allDrugs.size())*100;
			if (conceptsConsidered%4000==0) {
				LOGGER.info("Percentage Complete {}", (int)percComplete);
			}

			//DRUGS-585
			if (isMP(c) || isMPF(c)) {
				validateNoModifiedSubstances(c);
			}
		}
		LOGGER.info("MP MPF validation complete");
	}

	private void validateNoModifiedSubstances(Concept c) throws TermServerScriptException {
		String issueStr = c.getConceptType() + " has modified ingredient";
		initialiseSummary(issueStr);
		//Check all ingredients for any that themselves have modification relationships
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(HAS_PRECISE_INGRED) || r.getType().equals(HAS_ACTIVE_INGRED) ) {
				Concept ingredient = r.getTarget();
				for (Relationship ir :  ingredient.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (ir.getType().equals(IS_MODIFICATION_OF)) {
						report(c, issueStr, ingredient, "is modification of", ir.getTarget());
					}
				}
			}
		}
	}



}
