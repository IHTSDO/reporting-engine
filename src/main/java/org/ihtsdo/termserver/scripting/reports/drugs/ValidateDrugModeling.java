package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class ValidateDrugModeling extends TermServerReport{
	
	String drugsHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	String substHierarchyStr = "105590001"; // |Substance (substance)|
	
	private static final String[] badWords = new String[] { "preparation", "agent", "+", "product"};
	private static final String remodelledDrugIndicator = "Product containing";
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateDrugModeling report = new ValidateDrugModeling();
		try {
			report.additionalReportColumns = "Issue, Data, Detail";
			report.init(args);
			report.loadProjectSnapshot(false); //Load all descriptions
			report.validateDrugsModeling();
			report.validateSubstancesModeling();
		} catch (Exception e) {
			info("Failed to produce Druge Model Validation Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void validateDrugsModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = gl.getConcept(drugsHierarchyStr).getDescendents(NOT_SET);
		ConceptType[] drugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.CLINICAL_DRUG };
		//ConceptType[] drugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.CLINICAL_DRUG };
		//ConceptType[] drugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT_FORM};
		//ConceptType[] drugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT };
		long issueCount = 0;
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			
			if (concept.getConceptId().equals("346644007")) {
				debug ("Check here");
			}
			
			// DRUGS-281, DRUGS-282
			//issueCount += validateIngredientsInFSN(concept, drugTypes);  
			
			// DRUGS-267
			//issueCount += validateIngredientsAgainstBoSS(concept);
			
			// DRUGS-296
			/*if (concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) && 
				concept.getParents(CharacteristicType.STATED_RELATIONSHIP).get(0).equals(MEDICINAL_PRODUCT)) {
				issueCount += validateStatedVsInferredAttributes(concept, HAS_ACTIVE_INGRED, drugTypes);
				issueCount += validateStatedVsInferredAttributes(concept, HAS_MANUFACTURED_DOSE_FORM, drugTypes);
			}*/
			
			// DRUGS-288
			issueCount += validateAttributeValueCardinality(concept, HAS_ACTIVE_INGRED);
			
			//issueCount += checkForBadWords(concept);  //DRUGS-93
		}
		info ("Drugs validation complete.  Detected " + issueCount + " issues.");
	}
	
	private void validateSubstancesModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = gl.getConcept(substHierarchyStr).getDescendents(NOT_SET);
		long issueCount = 0;
		for (Concept concept : subHierarchy) {
			//issueCount += validateDisposition(concept);
			issueCount += checkForBadWords(concept);  //DRUGS-93
		}
		info ("Substances validation complete.  Detected " + issueCount + " issues.");
	}
	
	//Ensure that all stated dispositions exist as inferred, and visa-versa
	private long validateDisposition(Concept concept) {
		long issuesCount = 0;
		issuesCount += validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.STATED_RELATIONSHIP);

		//If this concept has one or more hasDisposition attributes, check if the inferred parent has the same.
		if (concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_DISPOSITION, ActiveState.ACTIVE).size() > 0) {
			issuesCount += validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.INFERRED_RELATIONSHIP);
			issuesCount += checkForOddlyInferredParent(concept, HAS_DISPOSITION);
		}
		return issuesCount;
	}

	private long validateAttributeViewsMatch(Concept concept,
			Concept attributeType,
			CharacteristicType fromCharType) {
		//Check that all relationships of the given type "From" match "To"
		long issuesCount = 0;
		CharacteristicType toCharType = fromCharType.equals(CharacteristicType.STATED_RELATIONSHIP)? CharacteristicType.INFERRED_RELATIONSHIP : CharacteristicType.STATED_RELATIONSHIP;
		for (Relationship r : concept.getRelationships(fromCharType, attributeType, ActiveState.ACTIVE)) {
			if (findRelationship(concept, r, toCharType) == null) {
				String msg = fromCharType.toString() + " has no counterpart";
				report (concept, msg, r.toString());
				issuesCount++;
			}
		}
		return issuesCount;
	}
	

	/**
	 * list of concepts that have an inferred parent with a stated attribute 
	 * that is not the same as the that of the concept.
	 * @return
	 */
	private long checkForOddlyInferredParent(Concept concept, Concept attributeType) {
		//Work through inferred parents
		long issuesCount = 0;
		for (Concept parent : concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Find all STATED attributes of interest
			for (Relationship parentAttribute : parent.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				//Does our original concept have that attribute?  Report if not.
				if (null == findRelationship(concept, parentAttribute, CharacteristicType.STATED_RELATIONSHIP)) {
					String msg ="Inferred parent has a stated attribute not stated in child.";
					report (concept, msg, parentAttribute.toString());
					issuesCount++;
				}
			}
		}
		return issuesCount;
	}

	
	private Relationship findRelationship(Concept concept, Relationship exampleRel, CharacteristicType charType) {
		//Find the first relationship matching the type, target and activeState
		for (Relationship r : concept.getRelationships(charType, exampleRel.getType(),  ActiveState.ACTIVE)) {
			if (r.getTarget().equals(exampleRel.getTarget())) {
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
	private long checkForBadWords(Concept concept) {
		long issueCount = 0;
		//Check if we're product containing and then look for bad words
		if (concept.getFsn().contains(remodelledDrugIndicator)) {
			for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
				String term = d.getTerm();
				if (d.getType().equals(DescriptionType.FSN)) {
					term = SnomedUtils.deconstructFSN(term)[0];
				}
				for (String badWord : badWords ) {
					if (term.contains(badWord)) {
						String msg = "Term contains bad word: " + badWord;
						issueCount++;
						report (concept, msg, d.toString());
					}
				}
			}
		}
		return issueCount;
	}

	private int validateStatedVsInferredAttributes(Concept concept,
			Concept attributeType, ConceptType[] drugTypes) {
		int issueCount = 0;
		if (drugTypes==null || SnomedUtils.isConceptType(concept, drugTypes)) {
			List<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			List<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String msg = "Cardinality mismatch stated vs inferred " + attributeType;
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				issueCount++;
				report (concept, msg, data);
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
						String msg = "Stated " + statedAttribute.getType() + " is not present in inferred view";
						String data = statedAttribute.toString();
						issueCount++;
						report (concept, msg, data);
					}
				}
			}
		}
		return issueCount;
	}

	private int validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		int issueCount = 0;
		List<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_BOSS, ActiveState.ACTIVE);

		//Check BOSS attributes against active ingredients - must be in the same relationship group
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			incrementSummaryInformation("BoSS attributes checked");
			boolean matchFound = false;
			Concept boSS = bRel.getTarget();
			for (Relationship iRel : ingredientRels) {
				Concept ingred = iRel.getTarget();
				if (bRel.getGroupId() == iRel.getGroupId()) {
					boolean isSelf = boSS.equals(ingred);
					boolean isSubType = descendantsCache.getDescendents(boSS).contains(ingred);
					boolean isModificationOf = DrugUtils.isModificationOf(ingred, boSS);
					
					if (isSelf || isSubType || isModificationOf) {
						matchFound = true;
						if (isSubType) {
							incrementSummaryInformation("Active ingredient is a subtype of BoSS");
							String issue = "Active ingredient is a subtype of BoSS.  Expected modification.";
							report (concept, issue, ingred, boSS);
						} else if (isModificationOf) {
							incrementSummaryInformation("Valid Ingredients as Modification of BoSS");
						} else if (isSelf) {
							incrementSummaryInformation("BoSS matches ingredient");
						}
					}
				}
			}
			if (!matchFound) {
				String issue = "Basis of Strength not equal or subtype of active ingredient, neither is active ingredient a modification of the BoSS";
				report (concept, issue, null, boSS);
				issueCount++;
			}
		}
		return issueCount;
	}

	private int validateIngredientsInFSN(Concept c, ConceptType[] drugTypes) throws TermServerScriptException {
		int issueCount = 0;
		
		//Only check FSN for certain drug types (to be expanded later)
		if (!SnomedUtils.isConceptType(c, drugTypes)) {
			incrementSummaryInformation("Concepts ignored - wrong type");
			return issueCount;
		}
		incrementSummaryInformation("Concepts validated to ensure ingredients correct in FSN");
		Description currentFSN = c.getFSNDescription();
		termGenerator.setQuiet(true);
		Concept clone = c.clone();
		termGenerator.ensureDrugTermsConform(null, clone, CharacteristicType.STATED_RELATIONSHIP);
		Description proposedFSN = clone.getFSNDescription();
		
		if (!currentFSN.getTerm().equals(proposedFSN.getTerm())) {
			String issue = "FSN did not match expected pattern";
			String differences = findDifferences (currentFSN.getTerm(), proposedFSN.getTerm());
			report (c, issue, proposedFSN.getTerm(), differences);
			issueCount++;
		}
		return issueCount;
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
					differences += actuals[x] + " ";
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

	private int validateAttributeValueCardinality(Concept concept, Concept activeIngredient) throws TermServerScriptException {
		int issuesEncountered = 0;
		issuesEncountered += checkforRepeatedAttributeValue(concept, CharacteristicType.INFERRED_RELATIONSHIP, activeIngredient);
		issuesEncountered += checkforRepeatedAttributeValue(concept, CharacteristicType.STATED_RELATIONSHIP, activeIngredient);
		return issuesEncountered;
	}

	private int checkforRepeatedAttributeValue(Concept c, CharacteristicType charType, Concept activeIngredient) throws TermServerScriptException {
		int issues = 0;
		Set<Concept> valuesEncountered = new HashSet<Concept>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				String msg = "Multiple " + charType + " instances of active ingredient";
				report(c, msg, target.toString());
				issues++;
			}
			valuesEncountered.add(target);
		}
		return issues;
	}
	
	private void report (Concept c, String data, String detail) {
		super.report(c, c.getConceptType(), data, detail);
	}

/*	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		writeToReportFile ("Concept, FSN, SemTag, Issue, Data");
		
		//Recover static concepts that we'll need to search for in attribute types
		activeIngredient = gl.getConcept(SCTID_ACTIVE_INGREDIENT);
		hasManufacturedDoseForm = gl.getConcept(SCTID_MAN_DOSE_FORM);
		boss = gl.getConcept(SCTID_HAS_BOSS);
		hasDisposition = gl.getConcept(SCTID_HAS_DISPOSITION);
	}*/
}
