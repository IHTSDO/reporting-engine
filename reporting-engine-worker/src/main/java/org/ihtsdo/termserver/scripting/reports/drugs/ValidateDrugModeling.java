package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.drugs.Ingredient;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.TermGenerator;
import org.snomed.otf.scheduler.domain.*;

public class ValidateDrugModeling extends TermServerReport implements ReportClass {
	
	Concept [] solidUnits = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	Concept [] liquidUnits = new Concept [] { MILLILITER, LITER };
	String[] semTagHiearchy = new String[] { "(product)", "(medicinal product)", "(medicinal product form)", "(clinical drug)" };
	
	private static final String[] badWords = new String[] { "preparation", "agent", "+"};
	private static final String remodelledDrugIndicator = "Product containing";
	private static final String BOSS_FAIL = "BoSS failed to relate to ingredient";
	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	
	TermGenerator termGenerator = new DrugTermGenerator(this);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(ValidateDrugModeling.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3";  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Detail",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.DRUGS),
						"Drugs validation",
						"This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy.",
						params);
	}
	
	public void runJob() throws TermServerScriptException {
		validateDrugsModeling();
		validateSubstancesModeling();
		populateSummaryTab();
		info("Summary tab complete, all done.");
	}
	
	private void validateDrugsModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = MEDICINAL_PRODUCT.getDescendents(NOT_SET);
		ConceptType[] allDrugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.CLINICAL_DRUG };
		ConceptType[] cds = new ConceptType[] { ConceptType.CLINICAL_DRUG };  //DRUGS-267
		initialiseSummaryInformation(BOSS_FAIL);
		
		//for (Concept concept : Collections.singleton(gl.getConcept("418860009"))) {
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			
			//DRUGS-585
			if (concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
					concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
				validateNoModifiedSubstances(concept);
			}
			
			// DRUGS-281, DRUGS-282, DRUGS-269
			if (!concept.getConceptType().equals(ConceptType.PRODUCT)) {
				validateTerming(concept, allDrugTypes);  
			}
			
			// DRUGS-267
			validateIngredientsAgainstBoSS(concept);
			
			// DRUGS-296 
			if (concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) && 
				concept.getParents(CharacteristicType.STATED_RELATIONSHIP).get(0).equals(MEDICINAL_PRODUCT)) {
				validateStatedVsInferredAttributes(concept, HAS_ACTIVE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(concept, HAS_PRECISE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(concept, HAS_MANUFACTURED_DOSE_FORM, allDrugTypes);
			}
			
			//DRUGS-603: DRUGS-686 - Various modelling rules
			if (concept.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				validateCdModellingRules(concept);
			}
			
			//DRUGS-518
			if (SnomedUtils.isConceptType(concept, cds)) {
				checkForInferredGroupsNotStated(concept);
			}
			
			//DRUGS-51?
			if (concept.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				validateConcentrationStrength(concept);
			}
			
			// DRUGS-288
			validateAttributeValueCardinality(concept, HAS_ACTIVE_INGRED);
			
			//DRUGS-93, DRUGS-759
			checkForBadWords(concept);  
			
			//DRUGS-629
			checkForSemTagViolations(concept);
		}
		info ("Drugs validation complete");
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(e -> e.getValue())
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
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
		String issueStr = "Group contains > 1 presentation/concentration strength";
		String issue2Str = "Group contains > 1 presentation/concentration strength";
		String issue3Str = "Invalid drugs model";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		initialiseSummary(issue3Str);
		
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (g.isGrouped()) {
				List<Relationship> ps = g.getType(HAS_PRES_STRENGTH_VALUE);
				List<Relationship> psdu = g.getType(HAS_PRES_STRENGTH_DENOM_UNIT);
				List<Relationship> csdu = g.getType(HAS_CONC_STRENGTH_DENOM_UNIT);
				List<Relationship> csnu = g.getType(HAS_CONC_STRENGTH_UNIT);
				if (psdu.size() > 1 || csdu.size() > 1) {
					report(c, issueStr, g);
					return;
				} 
				if (c.getFsn().toLowerCase().contains("actuation")) {
					if (ps.size() < 1 || psdu.size() < 1) {
						report(c, issue2Str, g);
						return;
					}
				}
				if (psdu.size() == 1 && psdu.get(0).getTarget().equals(MILLILITER)) {
					report (c, issue3Str, psdu.get(0));
				}
				if (csdu.size() == 1 && csdu.get(0).getTarget().equals(gl.getConcept("732936001|Tablet|"))) {
					report (c, issue3Str, csdu.get(0));
				}
				if (psdu.size() == 1 && psdu.get(0).getTarget().equals(MILLIGRAM)) {
					report (c, issue3Str, psdu.get(0));
				}
				if (csnu.size() == 1 && csnu.get(0).getTarget().equals(gl.getConcept("258727004|milliequivalent|"))) {
					report (c, issue3Str, csdu.get(0));
				}
				if (csnu.size() == 1 && csnu.get(0).getTarget().equals(gl.getConcept("258728009|microequivalent|"))) {
					report (c, issue3Str, csdu.get(0));
				}
				if (csnu.size() == 1 && csnu.get(0).getTarget().equals(gl.getConcept("258718000|millimole|"))) {
					report (c, issue3Str, csdu.get(0));
				}
			}
		}
	}

	private void validateNoModifiedSubstances(Concept c) throws TermServerScriptException {
		String issueStr = c.getConceptType() + " has modified ingredient";
		initialiseSummary(issueStr);
		//Check all ingredients for any that themselves have modification relationships
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(HAS_PRECISE_INGRED)) {
				Concept ingredient = r.getTarget();
				for (Relationship ir :  ingredient.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (ir.getType().equals(IS_MODIFICATION_OF)) {
						report (c, issueStr, ingredient);
					}
				}
			}
		}
	}

	/**
	 * For Pattern 2A Drugs (liquids) where we have both a presentation strength and a concentration
	 * report these values and confirm if the units change between the two, and if the calculation is correct
	 * @param concept
	 * @return
	 * @throws TermServerScriptException 
	 */
	private void validateConcentrationStrength(Concept c) throws TermServerScriptException {
		String issueStr = "Presentation/Concentration mismatch";
		initialiseSummary(issueStr);
		//For each group, do we have both a concentration and a presentation?
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			Ingredient i = DrugUtils.getIngredientDetails(c, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (i.presStrength != null && i.concStrength != null) {
				boolean unitsChange = false;
				boolean issueDetected = false;
				//Does the unit change between presentation and strength?
				if (!i.presNumeratorUnit.equals(i.concNumeratorUnit) || ! i.presDenomUnit.equals(i.concDenomUnit)) {
					unitsChange = true;
				}
				
				//Normalise the numbers
				BigDecimal presStrength = new BigDecimal(DrugUtils.getConceptAsNumber(i.presStrength));
				BigDecimal concStrength = new BigDecimal(DrugUtils.getConceptAsNumber(i.concStrength));
				if (!i.presNumeratorUnit.equals(i.concNumeratorUnit)) {
					concStrength = concStrength.multiply(calculateUnitFactor (i.presNumeratorUnit, i.concNumeratorUnit));
				}
				
				BigDecimal presDenomQuantity = new BigDecimal (DrugUtils.getConceptAsNumber(i.presDenomQuantity));
				BigDecimal concDenomQuantity = new BigDecimal (DrugUtils.getConceptAsNumber(i.concDenomQuantity));
				if (!i.presDenomUnit.equals(i.concDenomUnit)) {
					concDenomQuantity = concDenomQuantity.multiply(calculateUnitFactor (i.presDenomUnit, i.concDenomUnit));
				}
				
				//Do they give the same ratio when we divide
				BigDecimal presRatio = presStrength.divide(presDenomQuantity, 3, RoundingMode.HALF_UP);
				BigDecimal concRatio = concStrength.divide(concDenomQuantity, 3, RoundingMode.HALF_UP);
				
				if (!presRatio.equals(concRatio)) {
					issueDetected = true;
				}
				report (c, issueStr, i.substance, i.presToString(), i.concToString(), unitsChange, issueDetected, issueDetected? presRatio + " vs " + concRatio : "");
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
			
			factor = unit1Idx > unit2Idx ? new BigDecimal(0.001D) : new BigDecimal(1000) ; 
		}
		return factor;
	}

	private void validateSubstancesModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = SUBSTANCE.getDescendents(NOT_SET);
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			validateDisposition(concept);
			checkForBadWords(concept);  //DRUGS-93
		}
		info ("Substances validation complete.");
	}
	
	//Ensure that all stated dispositions exist as inferred, and visa-versa
	private void validateDisposition(Concept concept) throws TermServerScriptException {
		validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.STATED_RELATIONSHIP);

		//If this concept has one or more hasDisposition attributes, check if the inferred parent has the same.
		if (concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_DISPOSITION, ActiveState.ACTIVE).size() > 0) {
			validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.INFERRED_RELATIONSHIP);
			checkForOddlyInferredParent(concept, HAS_DISPOSITION, true);
		}
	}

	private void validateAttributeViewsMatch(Concept concept,
			Concept attributeType,
			CharacteristicType fromCharType) throws TermServerScriptException {
		String issueStr = fromCharType.toString() + " has no counterpart";
		initialiseSummary(issueStr);
		//Check that all relationships of the given type "From" match "To"
		CharacteristicType toCharType = fromCharType.equals(CharacteristicType.STATED_RELATIONSHIP)? CharacteristicType.INFERRED_RELATIONSHIP : CharacteristicType.STATED_RELATIONSHIP;
		for (Relationship r : concept.getRelationships(fromCharType, attributeType, ActiveState.ACTIVE)) {
			if (findRelationship(concept, r, toCharType, false) == null) {
				report (concept, issueStr, r.toString());
			}
		}
	}

	/**
	 * list of concepts that have an inferred parent with a stated attribute 
	 * that is not the same as the that of the concept.
	 * @return
	 * @throws TermServerScriptException 
	 */
	private void checkForOddlyInferredParent(Concept concept, Concept attributeType, boolean allowMoreSpecific) throws TermServerScriptException {
		String issueStr ="Inferred parent has a stated attribute not stated in child.";
		initialiseSummary(issueStr);
		//Work through inferred parents
		for (Concept parent : concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Find all STATED attributes of interest
			for (Relationship parentAttribute : parent.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				//Does our original concept have that attribute?  Report if not.
				if (null == findRelationship(concept, parentAttribute, CharacteristicType.STATED_RELATIONSHIP, allowMoreSpecific)) {
					report (concept, issueStr, parentAttribute.toString());
					//Reporting one issue per concept is sufficient
					return;
				}
			}
		}
	}

	private Relationship findRelationship(Concept concept, Relationship exampleRel, CharacteristicType charType, boolean allowMoreSpecific) throws TermServerScriptException {
		//Find the first relationship matching the type, target and activeState
		for (Relationship r : concept.getRelationships(charType, exampleRel.getType(),  ActiveState.ACTIVE)) {
			if (allowMoreSpecific) {
				//Does this target value have the example rel as self or ancestor?
				Set<Concept> ancestorsOrSelf = gl.getAncestorsCache().getAncestorsOrSelf(r.getTarget());
				if (ancestorsOrSelf.contains(exampleRel.getTarget())) {
					return r;
				}
			} else if (r.getTarget().equals(exampleRel.getTarget())) {
				return r;
			}
		}
		return null;
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
		//Check if we're product containing and then look for bad words
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtils.deconstructFSN(term)[0];
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
						report (concept, issueStr, concept.getFsn().contains(remodelledDrugIndicator), d.toString());
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
		
		if (drugTypes==null || SnomedUtils.isConceptType(concept, drugTypes)) {
			List<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			List<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				report (concept, issueStr, data);
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
						report (concept, issue2Str, data);
					}
				}
			}
		}
	}

	private void validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		String issueStr = "Active ingredient is a subtype of BoSS.  Expected modification.";
		String issue2Str = "Basis of Strength not equal or subtype of active ingredient, neither is active ingredient a modification of the BoSS";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		List<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_BOSS, ActiveState.ACTIVE);
		//Check BOSS attributes against active ingredients - must be in the same relationship group
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			incrementSummaryInformation("BoSS attributes checked");
			boolean matchFound = false;
			Concept boSS = bRel.getTarget();
			for (Relationship iRel : ingredientRels) {
				Concept ingred = iRel.getTarget();
				if (bRel.getGroupId() == iRel.getGroupId()) {
					boolean isSelf = boSS.equals(ingred);
					boolean isSubType = gl.getDescendantsCache().getDescendents(boSS).contains(ingred);
					boolean isModificationOf = DrugUtils.isModificationOf(ingred, boSS);
					
					if (isSelf || isSubType || isModificationOf) {
						matchFound = true;
						if (isSubType) {
							incrementSummaryInformation("Active ingredient is a subtype of BoSS");
							report (concept, issueStr, ingred, boSS);
						} else if (isModificationOf) {
							incrementSummaryInformation("Valid Ingredients as Modification of BoSS");
						} else if (isSelf) {
							incrementSummaryInformation("BoSS matches ingredient");
						}
					}
				}
			}
			if (!matchFound) {
				report (concept, issue2Str, boSS);
				incrementSummaryInformation(BOSS_FAIL);
			}
		}
	}

	private void validateTerming(Concept c, ConceptType[] drugTypes) throws TermServerScriptException {
		//Only check FSN for certain drug types (to be expanded later)
		if (!SnomedUtils.isConceptType(c, drugTypes)) {
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
			debug ("Debug here - hit a null");
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
		String issue2Str = termName + " case significance does not meet expectations";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		if (!actual.getTerm().equals(expected.getTerm())) {
			
			String differences = findDifferences (actual.getTerm(), expected.getTerm());
			report (c, issueStr, expected.getTerm(), differences, actual);
		} else if (!actual.getCaseSignificance().equals(expected.getCaseSignificance())) {
			String detail = "Expected: " + SnomedUtils.translateCaseSignificanceFromEnum(expected.getCaseSignificance());
			detail += ", Actual: " + SnomedUtils.translateCaseSignificanceFromEnum(actual.getCaseSignificance());
			report (c, issue2Str, detail, actual);
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
		
		Set<Concept> valuesEncountered = new HashSet<Concept>();
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
			List<Relationship> unmatched = new ArrayList<>();
			for (Relationship r : unmatchedGroup.getRelationships()) {
				if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
					unmatched.add(r);
				}
			}
				String unmatchedStr = unmatched.stream().map(r -> r.toString(true)).collect(Collectors.joining(",\n"));
				report (c, issueStr,
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
					relA.getTarget().equals(relB.getTarget())) {
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
		initialiseSummary(issueStr);
		//Ensure that the hierarchical level of this semantic tag is the same or deeper than those of the parent
		int tagLevel = getTagLevel(c);
		for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int parentTagLevel = getTagLevel(p);
			if (tagLevel < parentTagLevel) {
				report (c, issueStr, p);
			}
		}
	}

	private int getTagLevel(Concept c) throws TermServerScriptException {
		String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		for (int i=0; i < semTagHiearchy.length; i++) {
			if (semTagHiearchy[i].equals(semTag)) {
				return i;
			}
		}
		//throw new TermServerScriptException("Unable to find semantic tag level for: " + c);
		error("Unable to find semantic tag level for: " + c, null);
		return NOT_SET;
	}
	
	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	protected void report (Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		super.report (PRIMARY_REPORT, c, details);
	}
	
}
