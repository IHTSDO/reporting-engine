package org.ihtsdo.termserver.scripting.reports.drugs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DrugsModelingAndTerming extends DrugsReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(DrugsModelingAndTerming.class);

	private static final ConceptType[] ALL_DRUG_TYPES = { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_ONLY, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.MEDICINAL_PRODUCT_FORM_ONLY, ConceptType.CLINICAL_DRUG };
	private static final ConceptType[] CD_ONLY = { ConceptType.CLINICAL_DRUG };  //DRUGS-267

	private static final String ISSUE_MULTI_STRENGTH = "Group contains > 1 presentation/concentration strength";
	private static final String ISSUE_INVALID_MODEL = "Invalid drugs model";

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(DrugsModelingAndTerming.class, args, params);
	}
	
	@Override
	public Job getJob() {
		return getDrugJob("Drugs Modeling and Terming",
				"This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy.");
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateDrugsModeling();
		validateTherapeuticRole();
		reportSummaryCounts(SECONDARY_REPORT, SUMMARY_SORT_ORDER.COUNT);
		LOGGER.info("Summary tab complete, all done.");
	}

	public void validateDrugsModeling() throws TermServerScriptException {
		double conceptsConsidered = 0;
		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			DrugUtils.setConceptType(c);
			double percComplete = (conceptsConsidered++ / allDrugs.size()) * 100;
			if (conceptsConsidered % 4000 == 0) {
				LOGGER.info("Percentage Complete {}", (int) percComplete);
			}
			validateDrug(c);
		}
		LOGGER.info("Drugs validation complete");
	}

	private boolean shouldSkipForVaccineMode(Concept c) throws TermServerScriptException {
		if (mode != Mode.VACCINE) return false;
		if (!c.getFsn().toLowerCase().contains("vaccine")) return true;
		if (isCD(c) || isMPF(c) || isMPFOnly(c)) {
			String issueStr = "Vaccines should only exist at MP/MPO level";
			initialiseSummaryInformation(issueStr);
			report(c, issueStr);
			return true;
		}
		return false;
	}

	private void validateDrug(Concept c) throws TermServerScriptException {
		if (shouldSkipForVaccineMode(c)) {
			return;
		}

		//INFRA-4159 Seeing impossible situation of no stated parents.  Also DRUGS-895
		if (c.getParents(CharacteristicType.STATED_RELATIONSHIP).isEmpty()) {
			String issueStr = "Concept appears to have no stated parents";
			initialiseSummaryInformation(issueStr);
			report(c, issueStr);
			return;
		}

		// DRUGS-281, DRUGS-282, DRUGS-269
		if (!c.getConceptType().equals(ConceptType.PRODUCT)) {
			validateTerming(c, ALL_DRUG_TYPES);
		}

		//DRUGS-296
		if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) &&
				c.getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next().equals(MEDICINAL_PRODUCT)) {
			validateStatedVsInferredAttributes(c, HAS_ACTIVE_INGRED, ALL_DRUG_TYPES);
			validateStatedVsInferredAttributes(c, HAS_PRECISE_INGRED, ALL_DRUG_TYPES);
			validateStatedVsInferredAttributes(c, HAS_MANUFACTURED_DOSE_FORM, ALL_DRUG_TYPES);
		}

		//DRUGS-603: DRUGS-686 - Various modelling rules
		//RP-186
		if (isCD(c)) {
			validateCdModellingRules(c);
		}

		//RP-189
		validateProductModellingRules(c);

		//DRUGS-518
		if (DrugUtils.isConceptType(c, CD_ONLY)) {
			checkForInferredGroupsNotStated(c);
		}

		//DRUGS-51?
		if (isCD(c)) {
			validateConcentrationStrength(c);
			validateStrengthNormalization(c);
		}

		if (DrugUtils.isConceptType(c, ALL_DRUG_TYPES)) {
			//RP-191
			ensureStatedInferredAttributesEqual(c);
			//RP-194, RP-484
			checkForPrimitives(c);
		}

		//DRUGS-288
		validateAttributeValueCardinality(c, HAS_ACTIVE_INGRED);

		//DRUGS-93, DRUGS-759, DRUGS-803
		checkForBadWords(c);

		//DRUGS-629, RP-187
		checkForSemTagViolations(c);

		//RP-175
		validateAttributeRules(c);

		if (isCD(c)) {
			//RP-188
			checkCdUnitConsistency(c);
			//RP-504
			checkMissingDoseFormGrouper(c);
		}
	}

	private void checkForPrimitives(Concept c) throws TermServerScriptException {
		String issueStr = "Primitive concept";
		initialiseSummary(issueStr);
		if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			report(c, issueStr);
		}
	}

	private void ensureStatedInferredAttributesEqual(Concept c) throws TermServerScriptException {
		//Get all stated and inferred relationships and remove ISA and PlaysRole
		//Before checking for equivalence
		String issueStr = "Stated attributes not identical to inferred";
		initialiseSummary(issueStr);
		Set<Relationship> statedAttribs = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		Set<Relationship> inferredAttribs = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		
		Relationship isA = new Relationship(IS_A, null);
		removeRels(isA, statedAttribs, true); //remove all instances
		removeRels(isA, inferredAttribs, true);
		
		Relationship playsRole = new Relationship(PLAYS_ROLE, null);
		removeRels(playsRole, statedAttribs, true); //remove all instances
		removeRels(playsRole, inferredAttribs, true);
		
		//Now loop through all the stated relationship and remove them from inferred.
		//The should all successfully remove, and the inferred rels should be empty at the end.
		for (Relationship r : statedAttribs) {
			boolean success = removeRels(r, inferredAttribs, false); //Just remove one
			if (!success) {
				report(c, issueStr, r);
			}
		}
		
		if (!inferredAttribs.isEmpty()) {
			report(c, issueStr, inferredAttribs.iterator().next());
		}
	}

	private boolean removeRels(Relationship removeMe, Set<Relationship> rels, boolean removeAll) {
		Set<Relationship> forRemoval = new HashSet<>();
		for (Relationship r : rels) {
			if (r.getType().equals(removeMe.getType()) &&
				(removeMe.getTarget() == null || r.equalsTargetOrValue(removeMe))){
				forRemoval.add(r);
				if (!removeAll) {
					break;
				}
			}
		}
		rels.removeAll(forRemoval);
		return !forRemoval.isEmpty();
	}

	/**
	*	Acutation should be modeled with presentation strength and unit of presentation.
	*	Has presentation strength denominator unit (attribute) cannot be 258773002|Milliliter (qualifier value)
	*	Has concentration strength denominator unit (attribute) cannot be 732936001|Tablet (unit of presentation)
	*	Has presentation strength denominator unit (attribute) cannot be 258684004|milligram (qualifier value)
	*	Has concentration strength numerator unit (attribute) cannot be 258727004|milliequivalent (qualifier value)
	 * @throws TermServerScriptException 
	*/
	private void validateCdModellingRules(Concept c) throws TermServerScriptException {
		String issue4Str = "CD with multiple inferred parents";
		String issue5Str = "CD missing Has Unit of Presentation attribute";
		String issue6Str = "CD >1 x Has Unit of Presentation attribute";

		initialiseSummary(ISSUE_MULTI_STRENGTH);
		initialiseSummary(ISSUE_INVALID_MODEL);
		initialiseSummary(issue4Str);
		initialiseSummary(issue5Str);
		initialiseSummary(issue6Str);

		Set<Relationship> unitsOfPresentation = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_UNIT_OF_PRESENTATION, ActiveState.ACTIVE);
		if (unitsOfPresentation.isEmpty()) {
			report(c, issue5Str);
		} else if (unitsOfPresentation.size() > 1) {
			report(c, issue6Str);
		}

		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (g.isGrouped() && validateCdRelationshipGroup(c, g)) {
				return;
			}
		}

		if (c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).size() > 1) {
			report(c, issue4Str, getParentsJoinedStr(c));
		}
	}

	// Returns true if a fatal issue was found and further group checking should stop.
	private boolean validateCdRelationshipGroup(Concept c, RelationshipGroup g) throws TermServerScriptException {
		Set<Relationship> ps   = g.getType(HAS_PRES_STRENGTH_VALUE);
		Set<Relationship> psdu = g.getType(HAS_PRES_STRENGTH_DENOM_UNIT);
		Set<Relationship> csdu = g.getType(HAS_CONC_STRENGTH_DENOM_UNIT);
		Set<Relationship> csnu = g.getType(HAS_CONC_STRENGTH_UNIT);

		if (psdu.size() > 1 || csdu.size() > 1) {
			report(c, ISSUE_MULTI_STRENGTH, g);
			return true;
		}
		if (c.getFsn().toLowerCase().contains("actuation") && (ps.isEmpty() || psdu.isEmpty())) {
			report(c, ISSUE_MULTI_STRENGTH, g);
			return true;
		}

		if (psdu.size() == 1 && psdu.iterator().next().getTarget().equals(MILLILITER)) {
			report(c, ISSUE_INVALID_MODEL, psdu.iterator().next());
		}
		if (csdu.size() == 1 && csdu.iterator().next().getTarget().equals(gl.getConcept("732936001|Tablet|"))) {
			report(c, ISSUE_INVALID_MODEL, csdu.iterator().next());
		}
		if (psdu.size() == 1 && psdu.iterator().next().getTarget().equals(MILLIGRAM)) {
			report(c, ISSUE_INVALID_MODEL, psdu.iterator().next());
		}
		if (csnu.size() == 1 && csnu.iterator().next().getTarget().equals(gl.getConcept("258727004|milliequivalent|"))) {
			report(c, ISSUE_INVALID_MODEL, csdu.iterator().next());
		}
		if (csnu.size() == 1 && csnu.iterator().next().getTarget().equals(gl.getConcept("258728009|microequivalent|"))) {
			report(c, ISSUE_INVALID_MODEL, csdu.iterator().next());
		}
		if (csnu.size() == 1 && csnu.iterator().next().getTarget().equals(gl.getConcept("258718000|millimole|"))) {
			report(c, ISSUE_INVALID_MODEL, csdu.iterator().next());
		}
		return false;
	}

	private String getParentsJoinedStr(Concept c) {
		return c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream()
				.map(Concept::getFsn)
				.collect(Collectors.joining(", \n"));
	}

	private void validateProductModellingRules(Concept c) throws TermServerScriptException {
		String issueStr = "Product has more than one manufactured dose form attribute in Inferred Form";
		String issueStr2 = "Product has more than one manufactured dose form attribute in Stated Form";
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		Concept targetType = gl.getConcept("411116001 |Has manufactured dose form (attribute)|");
		if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, targetType, ActiveState.ACTIVE).size() > 1) {
			report(c, issueStr);
		}
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, targetType, ActiveState.ACTIVE).size() > 1) {
			report(c, issueStr2);
		}
	}

	/**
	 * For Pattern 2A Drugs (liquids) where we have both a presentation strength and a concentration
	 * report these values and confirm if the units change between the two, and if the calculation is correct
	 */
	private void validateConcentrationStrength(Concept c) throws TermServerScriptException {
		String issueStr = "Presentation/Concentration mismatch";
		initialiseSummary(issueStr);
		//For each group, do we have both a concentration and a presentation?
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			ConcreteIngredient i = DrugUtils.getIngredientDetails(c, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (i.presStrength != null && i.concStrength != null) {
				boolean unitsChange = false;
				boolean issueDetected = false;
				//Does the unit change between presentation and strength?
				if (!i.presNumeratorUnit.equals(i.concNumeratorUnit) || ! i.presDenomUnit.equals(i.concDenomUnit)) {
					unitsChange = true;
				}
				
				//Normalise the numbers
				BigDecimal presStrength = new BigDecimal(i.presStrength);
				BigDecimal concStrength = new BigDecimal(i.concStrength);
				if (!i.presNumeratorUnit.equals(i.concNumeratorUnit)) {
					concStrength = concStrength.multiply(calculateUnitFactor (i.presNumeratorUnit, i.concNumeratorUnit));
				}
				
				BigDecimal presDenomQuantity = new BigDecimal (i.presDenomQuantity);
				BigDecimal concDenomQuantity = new BigDecimal (i.concDenomQuantity);
				if (!i.presDenomUnit.equals(i.concDenomUnit)) {
					concDenomQuantity = concDenomQuantity.multiply(calculateUnitFactor (i.presDenomUnit, i.concDenomUnit));
				}
				
				//Do they give the same ratio when we divide
				BigDecimal presRatio = presStrength.divide(presDenomQuantity, 3, RoundingMode.HALF_UP);
				BigDecimal concRatio = concStrength.divide(concDenomQuantity, 3, RoundingMode.HALF_UP);
				
				if (!presRatio.equals(concRatio)) {
					issueDetected = true;
				}
				report(c, issueStr, i.substance, i.presToString(), i.concToString(), unitsChange, issueDetected, issueDetected? presRatio + " vs " + concRatio : "");
			}
		}
	}

	private BigDecimal calculateUnitFactor(Concept unit1, Concept unit2) {
		BigDecimal factor = new BigDecimal(1);  //If we don't work out a different, multiple so strength unchanged
		//Is it a solid or liquid?
		
		int unit1Idx = ArrayUtils.indexOf(solidUnits, unit1);
		int unit2Idx = -1;
		
		if (unit1Idx != -1) {
			unit2Idx = ArrayUtils.indexOf(solidUnits, unit2);
		} else {
			//Try liquid
			unit1Idx = ArrayUtils.indexOf(liquidUnits, unit1);
			if (unit1Idx != -1) {
				unit2Idx = ArrayUtils.indexOf(liquidUnits, unit2);
			}
		}
		
		if (unit1Idx != -1) {
			if (unit2Idx == -1) {
				throw new IllegalArgumentException("Units lost between " + unit1 + " and " + unit2 );
			} else if (unit1Idx == unit2Idx) {
				throw new IllegalArgumentException("Units previously detected different between " + unit1 + " and " + unit2 );
			}
			
			factor = unit1Idx > unit2Idx ? BigDecimal.valueOf(0.001D) : new BigDecimal(1000) ;
		}
		return factor;
	}
	
	private void validateStrengthNormalization(Concept c) throws TermServerScriptException {
		//For each group, validate any relevant units
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			ConcreteIngredient i = DrugUtils.getIngredientDetails(c, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (i.presStrength != null) {
				validateStrengthNormalization(c, i.presNumeratorUnit, i.presStrength);
				validateStrengthNormalization(c, i.presDenomUnit, i.presDenomQuantity);
			}
		
			if(i.concStrength != null) {
				validateStrengthNormalization(c, i.concNumeratorUnit, i.concStrength);
				validateStrengthNormalization(c, i.concDenomUnit, i.concDenomQuantity);
			}
		}
	}
	

	private void validateStrengthNormalization(Concept c, Concept unit, String strengthStr) throws TermServerScriptException {
		String issueStr = "Strength Normalization Issue";
		initialiseSummary(issueStr);
		String issueStr2 = "Strength Normalization Issue >= 1000000 unit";
		initialiseSummary(issueStr2);
		
		//Are we working with a known solid or liquid unit?
		int unitIdx = ArrayUtils.indexOf(solidUnits, unit);
		if (unitIdx == -1) { //Try liquid
			unitIdx = ArrayUtils.indexOf(liquidUnits, unit);
		}
		
		if (unitIdx != -1) {
			Double strength = Double.parseDouble(strengthStr);
			if (strength > 1000 || strength < 1) {
				report(c, issueStr, strengthStr + " " + unit.getPreferredSynonym());
			}
		}
		
		//767525000 |Unit (qualifier value)|
		if (unit.getConceptId().equals("767525000")) {
			Double strength = Double.parseDouble(strengthStr);
			if (strength >= 1000000) {
				report(c, issueStr2, strengthStr + " " + unit.getPreferredSynonym());
			}
		}
	}

	/*
	Need to identify and update:
		FSN beginning with "Product containing" that includes any of the following in any active description:
		agent
		+
		preparation
		product (except in the semantic tag)
	 */
	private void checkForBadWords(Concept concept) throws TermServerScriptException {
		String issueStr2 = "Non-FSN starts with 'Product'";
		initialiseSummary(issueStr2);
		//Check if we're product containing and then look for bad words
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtilsBase.deconstructFSN(term)[0];
			} else if (term.startsWith("Product")) {
				report(concept, issueStr2, d);
			}
			
			for (String badWord : badWords ) {
				String issueStr = "Term contains bad word: " + badWord;
				initialiseSummary(issueStr);
				if (term.contains(badWord)) {
					//Exception, MP PT will finish with word "product"
					if (concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && d.isPreferred() && badWord.equals("product")) {
						continue;
					} else {
						if (badWord.equals("+") && isPlusException(term)) {
							continue;
						}
						report(concept, issueStr, d.toString());
						return;
					}
				}
			}
		}
	}

	private boolean isPlusException(String term) {
		//Various rules that allow a + to exist next to other characters
		if (term.contains("^+") ||
			term.contains("+)") ||
			term.contains("+]") ||
			term.contains("(+")) {
			return true;
		}
		return false;
	}

	private void validateStatedVsInferredAttributes(Concept concept,
			Concept attributeType, ConceptType[] drugTypes) throws TermServerScriptException {
		String issueStr = "Cardinality mismatch stated vs inferred " + attributeType;
		String issue2Str = "Stated X is not present in inferred view";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		if (drugTypes==null || DrugUtils.isConceptType(concept, drugTypes)) {
			Set<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			Set<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				report(concept, issueStr, data);
			} else {
				for (Relationship statedAttribute : statedAttributes) {
					boolean found = false;
					for (Relationship infAttribute : infAttributes) {
						if (statedAttribute.getTarget().equals(infAttribute.getTarget())) {
							found = true;
							break;
						}
					}
					if (!found) {
						issue2Str = "Stated " + statedAttribute.getType() + " is not present in inferred view";
						String data = statedAttribute.toString();
						report(concept, issue2Str, data);
					}
				}
			}
		}
	}

	private void validateTerming(Concept c, ConceptType[] drugTypes) throws TermServerScriptException {

		//Only check FSN for certain drug types (to be expanded later)
		if (!DrugUtils.isConceptType(c, drugTypes)) {
			incrementSummaryInformation("Concepts ignored - wrong type");
		}
		incrementSummaryInformation("Concepts validated to ensure ingredients correct in FSN");
		Description currentFSN = c.getFSNDescription();
		termGenerator.setQuiet(true);
		
		//Create a clone to be retermed, and then we can compare descriptions with the original	
		Concept clone = c.clone();
		termGenerator.ensureTermsConform(null, clone, CharacteristicType.STATED_RELATIONSHIP);
		Description proposedFSN = clone.getFSNDescription();
		compareTerms(c, "FSN", currentFSN, proposedFSN);
		Description ptUS = clone.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description ptGB = clone.getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (ptUS == null || ptUS.getTerm() == null || ptGB == null || ptGB.getTerm() == null) {
			LOGGER.debug("Debug here - hit a null");
			return;
		}
		if (ptUS.getTerm().equals(ptGB.getTerm())) {
			compareTerms(c, "PT", c.getPreferredSynonym(US_ENG_LANG_REFSET), ptUS);
		} else {
			compareTerms(c, "US-PT", c.getPreferredSynonym(US_ENG_LANG_REFSET), ptUS);
			compareTerms(c, "GB-PT", c.getPreferredSynonym(GB_ENG_LANG_REFSET), ptGB);
		}
	}
	
	private void compareTerms(Concept c, String termName, Description actual, Description expected) throws TermServerScriptException {
		String issueStr = termName + " does not meet expectations";
		String issue2Str = termName + " case significance incorrect";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		if (!actual.getTerm().equals(expected.getTerm())) {
			String differences = findDifferences (actual.getTerm(), expected.getTerm());
			report(c, issueStr, expected.getTerm(), differences, actual);
		} else if (!actual.getCaseSignificance().equals(expected.getCaseSignificance())) {
			String detail = "Expected: " + SnomedUtils.translateCaseSignificanceFromEnum(expected.getCaseSignificance());
			detail += ", Actual: " + SnomedUtils.translateCaseSignificanceFromEnum(actual.getCaseSignificance());
			report(c, issue2Str, detail, actual);
		}
	}

	private String findDifferences(String actual, String expected) {
		String differences = "";
		//For each word, see if it exists in the other 
		String[] actuals = actual.split(" ");
		String[] expecteds = expected.split(" ");
		int maxLoop = (actuals.length>expecteds.length)?actuals.length:expecteds.length;
		for (int x=0; x < maxLoop; x++) {
			if (actuals.length > x) {
				if (! expected.contains(actuals[x])) {
					differences += actuals[x] + " vs ";
				}
			}
			
			if (expecteds.length > x) {
				if (! actual.contains(expecteds[x])) {
					differences += expecteds[x] + " ";
				}
			}
		}
		return differences;
	}

	private void validateAttributeValueCardinality(Concept concept, Concept activeIngredient) throws TermServerScriptException {
		checkforRepeatedAttributeValue(concept, CharacteristicType.INFERRED_RELATIONSHIP, activeIngredient);
		checkforRepeatedAttributeValue(concept, CharacteristicType.STATED_RELATIONSHIP, activeIngredient);
	}

	private void checkforRepeatedAttributeValue(Concept c, CharacteristicType charType, Concept activeIngredient) throws TermServerScriptException {
		String issueStr = "Multiple " + charType + " instances of active ingredient";
		initialiseSummary(issueStr);
		
		Set<Concept> valuesEncountered = new HashSet<>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				report(c, issueStr, target.toString());
			}
			valuesEncountered.add(target);
		}
	}
	
	private int checkForInferredGroupsNotStated(Concept c) throws TermServerScriptException {
		String issueStr = "Inferred group not stated";
		initialiseSummary(issueStr);
		
		RelationshipGroup unmatchedGroup = null;
		Concept playsRole = gl.getConcept("766939001 |Plays role (attribute)|");
		//Work through all inferred groups and see if they're subsumed by a stated group
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		
		nextGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			//We expect "Plays Role" to be inherited, so filter those out 
			inferredGroup = filter(playsRole, inferredGroup);
			//Can we find a matching (or less specific but otherwise matching) stated group?
			for (RelationshipGroup statedGroup : statedGroups) {
				statedGroup = filter(playsRole, statedGroup);
				if (groupMatches(inferredGroup, statedGroup)) {
					continue nextGroup;
				}
			}
			//If we get to here, then an inferred group has not been matched by a stated one
			unmatchedGroup = inferredGroup;
		}
		
		if (unmatchedGroup != null) {
			//Which inferred relationship is not also stated?
			Set<Relationship> unmatched = new HashSet<>();
			for (Relationship r : unmatchedGroup.getRelationships()) {
				if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
					unmatched.add(r);
				}
			}
				String unmatchedStr = unmatched.stream().map(r -> r.toString(true)).collect(Collectors.joining(",\n"));
				report(c, issueStr,
						c.toExpression(CharacteristicType.STATED_RELATIONSHIP),
						c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP), unmatchedStr);
		}
		return unmatchedGroup == null ? 0 : 1;
	}
	
	private RelationshipGroup filter(Concept filterType, RelationshipGroup group) {
		RelationshipGroup filteredGroup = new RelationshipGroup(group.getGroupId());
		for (Relationship r : group.getRelationships()) {
			if (!r.getType().equals(filterType)) {
				filteredGroup.addRelationship(r);
			}
		}
		return filteredGroup;
	}

	private boolean groupMatches(RelationshipGroup a, RelationshipGroup b) {
		if (a.getRelationships().size() != b.getRelationships().size()) {
			return false;
		}
		//Can we find a match for every relationship? Ignore groupId
		nextRelationship:
		for (Relationship relA : a.getRelationships()) {
			for (Relationship relB : b.getRelationships()) {
				if (relA.getType().equals(relB.getType()) && 
					relA.equalsTargetOrValue(relB)) {
					continue nextRelationship;
				}
			}
			//If we get here then we've failed to find a match for relA
			return false;
		}
		return true;
	}
	
	private void checkForSemTagViolations(Concept c) throws TermServerScriptException {
		String issueStr =  "Has higher level semantic tag than parent";
		String issueStr2 = "Has semantic tag incompatible with that of parent";
		String issueStr3 = "Has prohibited parent";
		String issueStr4 = "Has parent with an incompatible semantic tag incompatible with that of parent";
		String issueStr5 = "Has invalid parent / semantic tag combination";
		String issueStr6 = "MPF-Only expected to have MPF (not only) and MP-Only as parents";
		
		initialiseSummary(issueStr);
		initialiseSummary(issueStr2);
		initialiseSummary(issueStr3);
		initialiseSummary(issueStr4);
		initialiseSummary(issueStr5);
		initialiseSummary(issueStr6);
		
		//Ensure that the hierarchical level of this semantic tag is the same or deeper than those of the parent
		int tagLevel = getTagLevel(c);
		for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int parentTagLevel = getTagLevel(p);
			if (tagLevel < parentTagLevel) {
				report(c, issueStr, p);
			}
		}
		
		if (isCD(c)) {
			validateParentSemTags(c, "(medicinal product form)", issueStr2);
		} else if (isMPOnly(c)) {
			validateParentSemTags(c, "(medicinal product)", issueStr2);
		} else if (isMPFOnly(c)) {
			//Complex one this.   An MPF-Only should have at least one parent which is an MPF (not only)
			//and at least one which is MP-Only. And no other parents.
			boolean hasMpfNotOnly = false;
			boolean hasMpOnly = false;
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (isMPF(parent) && !isMPFOnly(parent)) {
					hasMpfNotOnly = true;
				} else if (isMPOnly(parent)) {
					hasMpOnly = true;
				} else {
					report(c, issueStr6, parent);
					break;
				}
			}
			if (!hasMpfNotOnly && !hasMpOnly) {
				report(c, issueStr6, getParentsJoinedStr(c));
			}
		} 
		
		validateParentTagCombo(c, gl.getConcept("766779001 |Medicinal product categorized by disposition (product)|"), "(product)", issueStr5);
		validateParentTagCombo(c, gl.getConcept("763760008 |Medicinal product categorized by structure (product)| "), "(product)", issueStr5);
	}

	private void validateParentTagCombo(Concept c, Concept targetParent, 
			String targetSemtag, String issueStr) throws TermServerScriptException {
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (parent.equals(targetParent) && !semTag.equals(targetSemtag)) {
				report(c, issueStr, "Has parent " + targetParent, "but not expected semtag " + targetSemtag);
			}
		}
	}

	private void validateParentSemTags(Concept c, String requiredTag, String issueStr) throws TermServerScriptException {
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			String semTag = SnomedUtilsBase.deconstructFSN(parent.getFsn())[1];
			
			//RP-587 There is a case where a CD grouper exists eg for infusion and/or injection
			//Look for and/or in the parent's dose form
			Concept parentDoseForm = getMDF(parent, true);
			
			//This can fail where a concept might have an extra unwanted "product" parent
			//eg 1172826001 |Product containing precisely ambroxol acefyllinate 100 milligram/1 each conventional release oral capsule (clinical drug)|
			if (parentDoseForm != null && parentDoseForm.getFsn().contains("and/or")) {
				continue;
			}
			
			if (!semTag.equals(requiredTag)) {
				report(c, issueStr, "parent", parent.getFsn(), " expected tag", requiredTag);
			}
		}
	}
		
	private void validateAttributeRules(Concept c) throws TermServerScriptException {
		String issueStr =  "MP/MPF must have one or more 'Has active ingredient' attributes";
		initialiseSummary(issueStr);
		if ((isMP(c) || isMPF(c)) && 
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE).size() < 1) {
			report(c, issueStr);
		}
		
		issueStr =  "CD must have one or more 'Has precise active ingredient' attributes";
		initialiseSummary(issueStr);
		if (isCD(c) && c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE).size() < 1) {
			report(c, issueStr);
		}
		
		issueStr =  "MP/MPF must not feature any role groups";
		//We mean traditional role groups here, so filter out self grouped
		initialiseSummary(issueStr);
		if ((isMP(c) || isMPF(c))) {
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
				if (g.getGroupId() == UNGROUPED) {
					continue;
				}
				if (g.size() == 1 && g.getRelationships().iterator().next().getType().equals(HAS_ACTIVE_INGRED)) {
					continue;
				}
				report(c, issueStr, g);
			}
		}
		
		issueStr =  "CD/MPF must feature exactly 1 dose form";
		initialiseSummary(issueStr);
		if ((isMPF(c) || isCD(c)) && 
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE).size() != 1) {
			report(c, issueStr);
		}
		
		issueStr = "Incorrect attribute type";
		if (isMP(c) || isMPF(c)) {
			Concept[] allowedAttributes = isMP(c) ? mpValidAttributes : mpfValidAttributes;
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				//Is this relationship type allowed?
				boolean allowed = false;
				for (Concept allowedType : allowedAttributes) {
					if (allowedType.equals(r.getType())) {
						allowed = true;
					}
				}
				if (!allowed) {
					report(c, issueStr, r);
				}
			}
		}
		
		issueStr =  "MP/MPF must feature 'containing' or 'only' in the FSN and PT";
		initialiseSummary(issueStr);
		if (isMP(c) || isMPF(c)) { 
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.isPreferred()) {
					if (!d.getTerm().contains("containing") && !d.getTerm().contains("only")) {
						report(c, issueStr, d);
					}
				}
			}
		}
		
		issueStr =  "CD must feature 'precisely' in the FSN";
		initialiseSummary(issueStr);
		if (isCD(c) && !c.getFsn().contains("precisely")) { 
			report(c, issueStr);
		}
		
		issueStr = "Precise MP/MPF must feature exactly one count of base";
		initialiseSummary(issueStr);
		if ((isMPOnly(c) || isMPFOnly(c))
			&& c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) { 
			report(c, issueStr);
		}
		
		//In the case of a missing count of base, we will not have detected that this concept is MP/MPF Only
		//So we must fall back to using fsn lexical search
		issueStr = "'Only' and 'precisely' must have a count of base";
		initialiseSummary(issueStr);
		if (isCD(c)) {
			if (!c.getFsn().contains("only") && !c.getFsn().contains("precisely")) {
				report(c, "UNEXPECTED CONCEPT TYPE - missing 'only' or 'precisely'");
			} else if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) { 
				if (true);
				report(c, issueStr);
			}
		} else if ((isMP(c) || isMPF(c)) && 
				(c.getFsn().contains("only") || c.getFsn().contains("precisely")) &&
				c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() != 1) {
			report(c, issueStr);
		}
		
		issueStr = "Each rolegroup in a CD must feature four presentation or concentration attributes";
		initialiseSummary(issueStr);
		if (isCD(c)) {
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
				Set<Concept> thesePresAttributes = new HashSet<>(presAttributes);
				Set<Concept> theseConcAttributes = new HashSet<>(concAttributes);
				
				//We'll remove attributes from our local copy of these arrays until there are none left
				for (Relationship r : g.getRelationships()) {
					if (isPresAttribute(r.getType()) || isConcAttribute(r.getType())) {
						Set<Concept> attributeSet = isPresAttribute(r.getType()) ? thesePresAttributes : theseConcAttributes;
						if (attributeSet.contains(r.getType())) {
							attributeSet.remove(r.getType());
						} else {
							report(c, issueStr, r);
						}
					}
				}
				
				if (thesePresAttributes.size() != 4 && !thesePresAttributes.isEmpty()) {
					report(c, issueStr, g);
				}
				
				if (theseConcAttributes.size() != 4 && !theseConcAttributes.isEmpty()) {
					report(c, issueStr, g);
				}
			}
		}
		
	}
		
	boolean isPresAttribute(Concept type) {
		return presAttributes.contains(type);
	}
	
	boolean isConcAttribute(Concept type) {
		return concAttributes.contains(type);
	}
	
	
	private void checkCdUnitConsistency(Concept c) throws TermServerScriptException {
		String issueStr1 = "CD has > 1 unit of presentation";
		String issueStr2 = "CD has incorrect presentation denominator strength unit count";
		String issueStr3 = "CD has inconsistent presentation units";
		initialiseSummary(issueStr1);
		initialiseSummary(issueStr2);
		initialiseSummary(issueStr3);
		
		//Do we have a unit of presentation?
		Set<Relationship> unitsOfPres = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_UNIT_OF_PRESENTATION, ActiveState.ACTIVE);
		if (unitsOfPres.size() > 1) {
			report(c, issueStr1);
		} else if (unitsOfPres.size() == 1) {
			Concept unitOfPres = unitsOfPres.iterator().next().getTarget();
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
				if (!g.isGrouped() || g.size() == 1) {
					continue;
				}
				Set<Relationship> presDenomUnits = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRES_STRENGTH_DENOM_UNIT, g.getGroupId());
				if (presDenomUnits.size() != 1) {
					report(c, issueStr2, g);
				} else if (!unitOfPres.equals(presDenomUnits.iterator().next().getTarget())) {
					report(c, issueStr3, unitOfPres, g);
				}
				incrementSummaryInformation("CD groups checked for presentation unit consistency");
			}
		}
	}
	

	/** Identify Clinical drug concepts that have the same BoSS/PAI/strength but for which one the dose form contains 
	 * "injection" and the other "infusion" and for which there is not an inferred parent that has the same BoSS/PAI/strength 
	 * with a dose form that contains "infusion and/or injection".
	 * @throws TermServerScriptException 
	 */
	private void checkMissingDoseFormGrouper(Concept c) throws TermServerScriptException {
		//Does this CD's dose form contain the word injection or infusion?
		Concept doseForm = getDoseForm(c);
		if (doseForm.getFsn().toLowerCase().contains(INJECTION) && 
				!doseForm.getFsn().toLowerCase().contains(INFUSION)) {
			List<Concept> infusionSiblings = findInfusionSiblings(c);
			if (infusionSiblings.size() > 0) {
				validateDoseFormGrouperParent(c, infusionSiblings.get(0));
			}
			//Don't need to report the first one, already reported.
			boolean isFirst = true;
			for (Concept infusionSibling : infusionSiblings) {
				if (isFirst) {
					isFirst = false;
				} else {
					validateDoseFormGrouperParent(infusionSibling, c);
				}
			}
		}
	}

	private Concept getDoseForm(Concept c) throws TermServerScriptException {
		String issueStr1 = "Invalid count of dose form attributes";
		initialiseSummary(issueStr1);
		
		Set<Concept> doseForms = SnomedUtils.getTargets(c, doseFormTypes, CharacteristicType.STATED_RELATIONSHIP);
		if (doseForms.size() != 1) {
			report(c, issueStr1, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			throw new TermServerScriptException("Please fix invalid dose form modelling on " + c + " unable to continue.");
		}
		return doseForms.iterator().next();
	}

	private List<Concept> findInfusionSiblings(Concept c) throws TermServerScriptException {
		List<Concept> matchingSiblings = new ArrayList<>();
		nextConcept:
		for (Concept sibling : allDrugs) {
			DrugUtils.setConceptType(c);
			if (!isCD(sibling)) {
				continue nextConcept;
			}
			
			if (!sibling.getFsn().toLowerCase().contains(INFUSION) ||
				sibling.getFsn().toLowerCase().contains(INJECTION)) {
				continue nextConcept;
			}
			
			if (DrugUtils.matchesBossPAIStrength(c, sibling)) {
				matchingSiblings.add(sibling);
			}
		}
		return matchingSiblings;
	}
	


	private void validateDoseFormGrouperParent(Concept c, Concept sibling) throws TermServerScriptException {
		String issueStr = "CD Infusion/Injection pair missing grouper parent";
		initialiseSummary(issueStr);
		
		boolean hasGrouperParent = false;
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (!DrugUtils.matchesBossPAIStrength(c, parent)) {
				continue;
			}
			
			String parentDoseFormFsn = getDoseForm(parent).getFsn().toLowerCase();
			if (!parentDoseFormFsn.contains(INFUSION) || !parentDoseFormFsn.contains(INJECTION)) {
				continue;
			}
			hasGrouperParent = true;
			break;
		}
		if (!hasGrouperParent) {
			report(c, issueStr, "Sibling: " + sibling);
		}
	}

	private int getTagLevel(Concept c) throws TermServerScriptException {
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
		for (int i = 0; i < semTagHierarchy.length; i++) {
			if (semTagHierarchy[i].equals(semTag)) {
				return i;
			}
		}
		LOGGER.error("Unable to find semantic tag level for: {}", c, (Exception)null);
		return NOT_SET;
	}
	
	//RP-198
	public void validateTherapeuticRole() throws TermServerScriptException {
		String issueStr = "Descendant of therapeutic role should not be 'agent'";
		initialiseSummary(issueStr);
		Concept theraputicRole = gl.getConcept("766941000 |Therapeutic role (role)|");
		nextConcept:
		for (Concept c : theraputicRole.getDescendants(NOT_SET)) {
			boolean inVaccineMode = mode == Mode.VACCINE;
			boolean isVaccine = c.getFsn().toLowerCase().contains("vaccine");
			if (inVaccineMode != isVaccine) {
				continue;
			}

			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().toLowerCase().contains("agent")) {
					report(c, issueStr, d);
					continue nextConcept;
				}
			}
		}
	}
	
}
