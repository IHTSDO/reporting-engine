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

public class BoSSPAICheck extends DrugsReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(BoSSPAICheck.class);

	private final Set<BaseMDF> reportedBaseMDFCombos = new HashSet<>();

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(BoSSPAICheck.class, args, params);
	}

	@Override
	public Job getJob() {
		return getDrugJob("BoSS-PAI Validation",
				"This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy " +
						"around the Basis of Strength Substance and the Precise Active Ingredient.");
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateBoSSPai();
		reportSummaryCounts(SECONDARY_REPORT, SUMMARY_SORT_ORDER.COUNT);
		LOGGER.info("Summary tab complete, all done.");
	}

	public void validateBoSSPai() throws TermServerScriptException {
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
			
			//DRUGS-267
			validateIngredientsAgainstBoSS(c);
			//DRUGS-1021
			if (isCD(c)) {
				checkBossPaiPdfCombinations(c);
			}
			
			//DRUGS-793
			if (!c.getConceptType().equals(ConceptType.PRODUCT)) {
				checkForBossGroupers(c);
				checkForPaiGroupers(c);
			}
		}
		LOGGER.info("BoSS PAI validation complete");
	}

	private void checkForBossGroupers(Concept c) throws TermServerScriptException {
		String issueStr = "Grouper substance used as BoSS";
		initialiseSummary(issueStr);
		for (Concept boss : SnomedUtils.getTargets(c, new Concept[] {HAS_BOSS}, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (grouperSubstanceUsage.containsKey(boss)) {
				report(c, issueStr, boss, " identified as grouper in ", grouperSubstanceUsage.get(boss));
			}
		}
	}
	
	private void checkForPaiGroupers(Concept c) throws TermServerScriptException {
		String issueStr = "Grouper substance used as PAI";
		initialiseSummary(issueStr);
		for (Concept pai : SnomedUtils.getTargets(c, new Concept[] {HAS_PRECISE_INGRED}, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (grouperSubstanceUsage.containsKey(pai)) {
				report(c, issueStr, pai, " identified as grouper in ", grouperSubstanceUsage.get(pai));
			}
		}
	}

	private void validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		String issueStr  = "Active ingredient is a subtype of BoSS.  Expected modification.";
		String issue2Str = "Basis of Strength not equal or subtype of active ingredient, neither is active ingredient a modification of the BoSS";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		Set<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_BOSS, ActiveState.ACTIVE);
		//Check BOSS attributes against active ingredients - must be in the same relationship group
		Set<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			incrementSummaryInformation("BoSS attributes checked");
			boolean matchFound = false;
			Concept boSS = bRel.getTarget();
			for (Relationship iRel : ingredientRels) {
				Concept ingred = iRel.getTarget();
				if (bRel.getGroupId() == iRel.getGroupId()) {
					boolean isSelf = boSS.equals(ingred);
					boolean isSubType = gl.getDescendantsCache().getDescendants(boSS).contains(ingred);
					boolean isModificationOf = DrugUtils.isModificationOf(ingred, boSS);
					
					if (isSelf || isSubType || isModificationOf) {
						matchFound = true;
						if (isSubType) {
							incrementSummaryInformation("Active ingredient is a subtype of BoSS");
							report(concept, issueStr, ingred, boSS);
						} else if (isModificationOf) {
							incrementSummaryInformation("Valid Ingredients as Modification of BoSS");
						} else if (isSelf) {
							incrementSummaryInformation("BoSS matches ingredient");
						}
					}
				}
			}
			if (!matchFound) {
				report(concept, issue2Str, boSS);
			}
		}
	}
	
	private void checkBossPaiPdfCombinations(Concept concept) throws TermServerScriptException {
		String issueStr  = "BoSS-PAI combination differs";
		initialiseSummary(issueStr);
		
		for (RelationshipGroup rg : concept.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (!rg.isGrouped()) {
				continue;
			}
			//What is this BaseMDF?  Find all other RelGroups that have that same base and pharm dose form
			Concept mdf = getMDF(concept);
			BaseMDF baseMDF = getBaseMDF(rg, mdf);
			
			if (baseMDF == null) {
				LOGGER.debug("Failed to obtain baseMDF in {}", concept);
				continue;
			}
			
			if (reportedBaseMDFCombos.contains(baseMDF)) {
				continue;
			}
			
			Concept boSS = rg.getValueForType(HAS_BOSS);
			Concept pai =  rg.getValueForType(HAS_PRECISE_INGRED);
			BoSSPAI boSSPAI = new BoSSPAI(boSS, pai);
			Set<RelationshipGroup> relGroups = baseMDFMap.get(baseMDF);
			if (relGroups == null) {
				LOGGER.debug("Unable to find stored relGroups against {} from {}", baseMDF, concept);
			} else {
				StringBuilder mismatchingDetails = new StringBuilder();
				Set<BoSSPAI> bossPAIcombosReported = new HashSet<>();
				for (RelationshipGroup rg2 : relGroups) {
					//Now do we also match on Boss & PAI?
					Concept boSS2 = rg2.getValueForType(HAS_BOSS);
					Concept pai2 =  rg2.getValueForType(HAS_PRECISE_INGRED);
					BoSSPAI boSSPAI2 = new BoSSPAI(boSS2, pai2);
					if (bossPAIcombosReported.contains(boSSPAI2)) {
						continue;
					}
					if (!boSS.equals(boSS2) || !pai.equals(pai2)) {
						if (!mismatchingDetails.isEmpty()) {
							mismatchingDetails.append("\n");
						} else {
							//First time through, add the original boSSPAI as well as the matching one
							mismatchingDetails.append(boSSPAI).append("\n");
						}
						mismatchingDetails .append(boSSPAI2).append(" eg ").append(rg2.getSourceConcept().toStringPref());
						bossPAIcombosReported.add(boSSPAI2);
					}
				}
				if (!mismatchingDetails.isEmpty()) {
					report(concept, issueStr, baseMDF, mismatchingDetails);
					reportedBaseMDFCombos.add(baseMDF);
				}
			}
		}
	}

}
