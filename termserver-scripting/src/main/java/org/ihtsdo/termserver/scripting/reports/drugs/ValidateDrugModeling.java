package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class ValidateDrugModeling extends TermServerReport{
	
	String drugsHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	String substHierarchyStr = "105590001"; // |Substance (substance)|
	
	static final String SCTID_ACTIVE_INGREDIENT = "127489000"; // |Has active ingredient (attribute)|"
	static final String SCTID_HAS_BOSS = "732943007"; //Has basis of strength substance (attribute)
	static final String SCTID_MAN_DOSE_FORM = "411116001"; //Has manufactured dose form (attribute)
	static final String SCTID_HAS_DISPOSITION = "726542003"; // |Has disposition (attribute)|
	
	Concept activeIngredient;
	Concept hasManufacturedDoseForm;
	Concept boss;
	Concept hasDisposition;
	
	private static final String CD = "(clinical drug)";
	private static final String MP = "(medicinal product)";
	private static final String MPF = "(medicinal product form)";
	
	private static final String[] badWords = new String[] { "preparation", "agent", "+", "product"};
	private static final String remodelledDrugIndicator = "Product containing";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateDrugModeling report = new ValidateDrugModeling();
		try {
			report.init(args);
			report.loadProjectSnapshot(false); //Load all descriptions
			report.validateDrugsModeling();
			report.validateSubstancesModeling();
		} catch (Exception e) {
			println("Failed to produce Druge Model Validation Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} 
	}
	
	private void validateDrugsModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = gl.getConcept(drugsHierarchyStr).getDescendents(NOT_SET);
		String[] drugTypes = new String[] { MPF, CD };
		long issueCount = 0;
		for (Concept concept : subHierarchy) {
			//issueCount += validateIngredientsInFSN(concept);
			//issueCount += validateIngredientsAgainstBoSS(concept);
			//issueCount += validateStatedVsInferredAttributes(concept, activeIngredient, drugTypes);
			//issueCount += validateStatedVsInferredAttributes(concept, hasManufacturedDoseForm, drugTypes);
			//issueCount += validateAttributeValueCardinality(concept, activeIngredient);
			//issueCount += checkForBadWords(concept);
		}
		println ("Drugs validation complete.  Detected " + issueCount + " issues.");
	}
	
	private void validateSubstancesModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = gl.getConcept(substHierarchyStr).getDescendents(NOT_SET);
		long issueCount = 0;
		for (Concept concept : subHierarchy) {
			issueCount += validateDisposition(concept);
		}
		println ("Substances validation complete.  Detected " + issueCount + " issues.");
	}
	
	//Ensure that all stated dispositions exist as inferred, and visa-versa
	private long validateDisposition(Concept concept) {
		long issuesCount = 0;
		issuesCount += validateAttributeViewsMatch (concept, hasDisposition, CharacteristicType.STATED_RELATIONSHIP);

		//If this concept has one or more hasDisposition attributes, check if the inferred parent has the same.
		if (concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, hasDisposition, ActiveState.ACTIVE).size() > 0) {
			issuesCount += validateAttributeViewsMatch (concept, hasDisposition, CharacteristicType.INFERRED_RELATIONSHIP);
			issuesCount += checkForOddlyInferredParent(concept, hasDisposition);
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
			Concept attributeType, String[] drugTypes) {
		int issueCount = 0;
		if (isDrugType(concept, drugTypes)) {
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
		List<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, boss, ActiveState.ACTIVE);

		//Check BOSS attributes against active ingredients - must be in the same relationship group
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, activeIngredient, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			boolean matchFound = false;
			for (Relationship iRel : ingredientRels) {
				if (bRel.getGroupId() == iRel.getGroupId() && isSelfOrSubTypeOf(bRel.getTarget(), iRel.getTarget())) {
					matchFound = true;
				}
			}
			if (!matchFound) {
				String issue = "Basis of Strength not equal or subtype of active ingredient";
				report (concept, issue, bRel.getTarget().toString());
				issueCount++;
			}
		}
		return issueCount;
	}

	//Return true if concept c is equal or a subtype of the superType
	private boolean isSelfOrSubTypeOf(Concept c, Concept superType) throws TermServerScriptException {
		if (c.equals(superType)) {
			return true;
		}
		Set<Concept> subTypes = superType.getDescendents(NOT_SET);
		if (subTypes.contains(c)) {
			return true;
		}
		return false;
	}

	private int validateIngredientsInFSN(Concept concept) throws TermServerScriptException {
		int issueCount = 0;
		String[] drugTypes = new String[] { /*MP,*/ MPF};
		
		//Only check FSN for certain drug types (to be expanded later)
		if (!isDrugType(concept, drugTypes)) {
			return issueCount;
		}
		//Get all the ingredients and put them in order
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, activeIngredient, ActiveState.ACTIVE);
		Set<String> ingredients = new TreeSet<String>();  //Will naturally sort in alphabetical order
		for (Relationship r : ingredientRels) {
			//Need to recover the full concept to have all descriptions, not the partial one stored as the target.
			Description ingredientFSN = gl.getConcept(r.getTarget().getConceptId()).getFSNDescription();
			String ingredientName = SnomedUtils.deconstructFSN(ingredientFSN.getTerm())[0];
			//If the ingredient name is not case sensitive, decaptialize
			if (!ingredientFSN.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
				ingredientName = SnomedUtils.deCapitalize(ingredientName);
			}
			ingredients.add(ingredientName);
		}
		
		String proposedFSN = "Product containing ";
		proposedFSN += StringUtils.join(ingredients, " and ");
		
		//Do we need to add the dose form?
		if (isDrugType(concept, new String[]{MPF})) {
			proposedFSN += " in " + getDosageForm(concept);
		}
		
		proposedFSN += " " + SnomedUtils.deconstructFSN(concept.getFsn())[1];
		
		if (!concept.getFsn().equals(proposedFSN)) {
			String issue = "FSN did not match expected pattern";
			report (concept, issue, proposedFSN);
			issueCount++;
		}
		return issueCount;
	}
	
	private String getDosageForm(Concept concept) {
		List<Relationship> doseForms = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, hasManufacturedDoseForm, ActiveState.ACTIVE);
		if (doseForms.size() == 0) {
			return "NO STATED DOSE FORM DETECTED";
		} else if (doseForms.size() > 1) {
			return "MULTIPLE DOSE FORMS";
		} else {
			String doseForm = SnomedUtils.deconstructFSN(doseForms.get(0).getTarget().getFsn())[0];
			doseForm = SnomedUtils.deCapitalize(doseForm);
			//Translate known issues
			switch (doseForm) {
				case "ocular dosage form": doseForm =  "ophthalmic dosage form";
					break;
				case "inhalation dosage form": doseForm = "respiratory dosage form";
					break;
				case "cutaneous AND/OR transdermal dosage form" : doseForm = "topical dosage form";
					break;
				case "oromucosal AND/OR gingival dosage form" : doseForm = "oropharyngeal dosage form";
					break;
			}
			return doseForm;
		}
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

	private boolean isDrugType(Concept concept, String[] drugTypes) {
		boolean isType = false;
		for (String drugType : drugTypes) {
			if (SnomedUtils.deconstructFSN(concept.getFsn())[1].equals(drugType)) {
				isType = true;
				break;
			}
		}
		return isType;
	}

	protected void report (Concept c, String issue, String data) {
		String line =	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE +
						SnomedUtils.deconstructFSN(c.getFsn())[1] + QUOTE_COMMA_QUOTE +
						issue + QUOTE_COMMA_QUOTE +
						data + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		writeToFile ("Concept, FSN, SemTag, Issue, Data");
		
		//Recover static concepts that we'll need to search for in attribute types
		activeIngredient = gl.getConcept(SCTID_ACTIVE_INGREDIENT);
		hasManufacturedDoseForm = gl.getConcept(SCTID_MAN_DOSE_FORM);
		boss = gl.getConcept(SCTID_HAS_BOSS);
		hasDisposition = gl.getConcept(SCTID_HAS_DISPOSITION);
	}
}
