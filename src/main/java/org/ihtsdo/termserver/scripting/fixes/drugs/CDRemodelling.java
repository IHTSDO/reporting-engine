package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.HistoricalAssociation;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.TermVerifier;

import us.monoid.json.JSONObject;

/*
DRUGS-474 (Pattern 1a) Remodelling of Clinical Drugs including Basis of Strength Substance (BoSS) based on an input spreadsheet
DRUGS-493 (Pattern 1b) 
*/
public class CDRemodelling extends DrugBatchFix implements RF2Constants {
	
	Map<Concept, List<Ingredient>> spreadsheet = new HashMap<>();
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	IngredientCounts ingredientCounter;
	TermVerifier termVerifier;
	
	protected CDRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CDRemodelling fix = new CDRemodelling(null);
		try {
			fix.inputFileHasHeaderRow = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.ingredientCounter = new IngredientCounts(fix);
			fix.ingredientCounter.setPrintWriterMap(fix.printWriterMap);  //Share report file!
			fix.termGenerator.includeUnitOfPresentation(true);  //True for Pattern 1b   False for 3a
			//fix.termGenerator.specifyDenominator(true); //True for 3a
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't include the project export in our timings
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		if (inputFile2 == null) {
			warn ("No input file specified to verify terms");
		} else {
			termVerifier = new TermVerifier(inputFile2,this);
			termVerifier.init();
		}
	}


	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (loadedConcept.isActive() == false) {
			//Does this concept have an alternative/replacement that we should use instead?
			Concept alternative = getAlternative(task, concept);
			if (alternative == null) {
				report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive and no alternative (via hist assoc) available");
				return NO_CHANGES_MADE;
			}
			task.replace(concept, alternative);
			//We also need a copy of the original's spreadsheet data to look up, and also for the term verifier
			spreadsheet.put(alternative, spreadsheet.get(concept));
			if (termVerifier != null) {
				termVerifier.replace(concept, alternative);
			}
			loadedConcept = loadConcept(alternative, task.getBranchPath());
		} 
		
		changesMade = remodelConcept(task, loadedConcept);
		changesMade += ingredientCounter.assignIngredientCounts(task, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
		
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun?"Dry run updating":"Updating") + " state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getClass().getSimpleName()  + " - " + e.getMessage());
				e.printStackTrace();
			}
		}
		return changesMade;
	}

	private int remodelConcept(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		//The number of ingredients in this drug must match the number we have to model
		List<Relationship> ingredientRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		List<Ingredient> ingredients = spreadsheet.get(c);
		//Only worry about this if the model has more ingredients than the concept.  Otherwise we'll probably find them in the inferred form, 
		//and if not, we'll throw an error at the time.
		if (ingredientRels.size() > ingredients.size()) {
			report (t,c,Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Count mismatch in modelled (" + ingredients.size() + ") vs current (" + ingredientRels.size() + ") ingredients");
			return 0;
		}
		boolean isMultiIngredient = ingredients.size() > 1;
		for (Ingredient ingredient : ingredients) {
			changesMade += remodel(t,c,ingredient, isMultiIngredient);
		}
		
		//If we've made modeling changes, we can set this concept to be sufficiently defined if required
		if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (changesMade > 0) {
				c.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changesMade++;
				report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept set to be sufficiently defined");
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "No modelling changes made, skipping change to definition status.");
			}
		}
		
		//If we have any "active ingredients" left at this point, take them out now, before they confusing the terming
		for (Relationship ai : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
			removeRelationship(t,  ai, c);
		}
		//We're definitely going to have everything we need in the stated form, and in fact the inferred will be wrong because we haven't changed it.
		changesMade += termGenerator.ensureDrugTermsConform(t,c, CharacteristicType.STATED_RELATIONSHIP);
		if (termVerifier != null) {
			termVerifier.validateTerms(t, c);
		}
		return changesMade;
	}


	private int remodel(Task t, Concept c, Ingredient modelIngredient, boolean isMultiIngredient) throws TermServerScriptException {
		int changesMade = 0;
		int targetGroupId = SnomedUtils.getFirstFreeGroup(c);
		
		//Find this ingredient.  If it's in group 0, we need to move it to a new group
		Relationship substanceRel = getSubstanceRel(t, c, modelIngredient.substance, modelIngredient.boss);

		if (substanceRel == null) {
			//We'll need to create one!
			report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "No existing ingredient found.  Adding new relationship.");
			substanceRel = new Relationship (c, HAS_PRECISE_INGRED, modelIngredient.substance, targetGroupId);
		} else {
			//If we've found an active ingredient, inactivate it and replace with a PRECISE active ingredient
			if (substanceRel.getType().equals(HAS_ACTIVE_INGRED)) {
				removeRelationship(t, substanceRel, c);
				substanceRel = substanceRel.clone(null);
				substanceRel.setType(HAS_PRECISE_INGRED);
				substanceRel.setTarget(modelIngredient.substance);
				substanceRel.setActive(true);
			}
		}
		
		changesMade += replaceParents(t, c, MEDICINAL_PRODUCT);
		
		//Group Id must be > 0.  Set to next available if currently zero, or use the existing one otherwise.
		if (substanceRel.getGroupId() == 0) {
			substanceRel.setGroupId(targetGroupId);
			changesMade++;
		} else{
			targetGroupId = substanceRel.getGroupId();
		}
		
		//Have we added a new precise ingredient?
		if (!c.getRelationships().contains(substanceRel)) {
			report (t,c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, substanceRel);
			c.addRelationship(substanceRel);
			changesMade++;
		}
		
		changesMade += replaceRelationship(t, c, HAS_MANUFACTURED_DOSE_FORM, modelIngredient.doseForm, UNGROUPED, true);
		changesMade += replaceRelationship(t, c, HAS_BOSS, modelIngredient.boss, targetGroupId, false);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_VALUE, modelIngredient.strength, targetGroupId, false);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_UNIT, modelIngredient.numeratorUnit, targetGroupId, false);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_DENOM_VALUE, modelIngredient.denomQuantity, targetGroupId, false);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_DENOM_UNIT, modelIngredient.denomUnit, targetGroupId, false);
		if (modelIngredient.unitOfPresentation != null || termGenerator.includeUnitOfPresentation()) {
			changesMade += replaceRelationship(t, c, HAS_UNIT_OF_PRESENTATION, modelIngredient.unitOfPresentation, UNGROUPED, true);
		}
		return changesMade;
	}

	private Relationship getSubstanceRel(Task t, Concept c, Concept targetSubstance, Concept secondarySubstance) {
		List<Relationship> matchingRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, targetSubstance, ActiveState.ACTIVE);
		matchingRels.addAll( c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, targetSubstance, ActiveState.ACTIVE));

		if (matchingRels.size()==0) {
			//We could match an inferred relationship instead
			matchingRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, targetSubstance, ActiveState.ACTIVE);
			matchingRels.addAll( c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, targetSubstance, ActiveState.ACTIVE));
			if (matchingRels.size() > 0) {
				Relationship clone = matchingRels.get(0).clone(null);
				clone.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				clone.setGroupId(0);  //It'll get moved by the calling code
				return clone;
			} else {
				//Try matching on the secondary substance, the BoSS
				matchingRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, secondarySubstance, ActiveState.ACTIVE);
				matchingRels.addAll( c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, secondarySubstance, ActiveState.ACTIVE));
				//Can again try the inferred rels, with the BoSS
				if (matchingRels.size()==0) {
					matchingRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, secondarySubstance, ActiveState.ACTIVE);
					matchingRels.addAll( c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, secondarySubstance, ActiveState.ACTIVE));
					if (matchingRels.size() > 0) {
						Relationship clone = matchingRels.get(0).clone(null);
						clone.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
						clone.setGroupId(0);  //It'll get moved by the calling code
						return clone;
					}
				}
			}
		}
		
		if (matchingRels.size()!=1) {
			if (matchingRels.size() > 1) {
				report (t,c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to uniquely identify ingredient: " + targetSubstance + ". Found " + matchingRels.size());
			}
			return null;
		}
		return matchingRels.get(0);
	}
	
	/**
	 * PATTERN 1A
	 * Spreadsheet columns:
	 * [0]conceptId	[1]FSN	[2]Sequence	[3]dose_form_evaluation	[4]pharmaceutical_df	
	 * [5]future_ingredient	[6]future_boss	[7]future_Presentation_num_qty	
	 * [8]future_Presentation_num_unit	[9]future_Presentation_denom_qty	
	 * [10]future_Presentation_denom_unit	[11]future_unit_of_presentation	
	 * [12]dmd_ingredient	[13]stated_ingredient	[14]inferred_ingredient	
	 * [15]dmd_boss	[16]Unit_of_presentation	[17]dmd_numerator_quantity	
	 * [18]dmd_numerator_unit	[19]dmd_denominator_quantity	[20]dmd_denominator_unit	
	 * [21]stated_dose_form	[22]Pattern	[23]Source	[24]status	[25]Comment	[26]Transfer
	 
	protected List<Concept> loadLine(String[] items) throws TermServerScriptException {
		Concept c = gl.getConcept(items[0]);
		c.setConceptType(ConceptType.CLINICAL_DRUG);
		
		//Is this the first time we've seen this concept?
		if (!spreadsheet.containsKey(c)) {
			spreadsheet.put(c, new ArrayList<Ingredient>());
		}
		List<Ingredient> ingredients = spreadsheet.get(c);
		try {
			// Booleans indicate: don't create and do validate that concept exists
			Ingredient ingredient = new Ingredient();
			ingredient.doseForm = getPharmDoseForm(items[4]);
			ingredient.substance = gl.getConcept(items[5], false, true);
			ingredient.boss = gl.getConcept(items[6], false, true);
			ingredient.strength = DrugUtils.getNumberAsConcept(items[7]);
			ingredient.numeratorUnit = DrugUtils.findUnit(items[8]);
			ingredient.denomQuantity = DrugUtils.getNumberAsConcept(items[9]);
			ingredient.denomUnit = getUnitOfPresentation(items[10]);
			ingredient.unitOfPresentation = getUnitOfPresentation(items[11]);
			ingredients.add(ingredient);
		} catch (Exception e) {
			report (null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, e.getMessage());
			return null;
		}
		
		return Collections.singletonList(c);
	}*/
	
	@Override
	/**
	 * PATTERN 1B - https://docs.google.com/spreadsheets/d/1EqZg1-Ksjy5J-Iebnjry96PgL7MbL_Au85oBXPtHCgE/edit#gid=0
	 * Spreadsheet columns: 	[0]conceptId [1]FSN	[2]dose_form_evaluation	
	 * [3]dmd_name	[4]Precise_ingredient	[5]dmd_boss	[6]ConcNumUnit	
	 * [7]ConcNumQty	 [8]ConcDenomUnit	 [9]PRESENTNumQty	[10]ConcDenomQty	
	 * [11]PRESENTNumUnit [12]PRESENTDenomQty	[13]PRESENTDenomUnit	
	 * [14]UoP	[15]DoseForm
	 */
	protected List<Concept> loadLine(String[] items) throws TermServerScriptException {
		Concept c = gl.getConcept(items[0]);
		c.setConceptType(ConceptType.CLINICAL_DRUG);
		
		//Is this the first time we've seen this concept?
		if (!spreadsheet.containsKey(c)) {
			spreadsheet.put(c, new ArrayList<Ingredient>());
		}
		List<Ingredient> ingredients = spreadsheet.get(c);
		try {
			// Booleans indicate: don't create and do validate that concept exists
			Ingredient ingredient = new Ingredient();
			ingredient.doseForm = getPharmDoseForm(items[15]);
			ingredient.substance = gl.getConcept(items[4], false, true);
			ingredient.boss = gl.getConcept(items[5], false, true);
			ingredient.strength = DrugUtils.getNumberAsConcept(items[9]);
			ingredient.numeratorUnit = DrugUtils.findUnitOfMeasure(items[11]);
			ingredient.denomQuantity = DrugUtils.getNumberAsConcept(items[12]);
			ingredient.denomUnit = getUnitOfPresentation(items[13]);
			ingredient.unitOfPresentation = getUnitOfPresentation(items[14]);
			ingredients.add(ingredient);
		} catch (Exception e) {
			report (null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, e.getMessage());
			return null;
		}
		
		return Collections.singletonList(c);
	}
	
	/**
	 * PATTERN 3A - https://docs.google.com/spreadsheets/d/1EqZg1-Ksjy5J-Iebnjry96PgL7MbL_Au85oBXPtHCgE/edit#gid=0
	 * Spreadsheet columns: [0]conceptId	[10]FSN	[2]dose_form_evaluation	[3]dmd_name	
	 * [4]Precise_ingredient	 [5]dmd_boss	[6]ConcNumQty	[7]ConcNumUnit	[8]ConcDenomQty	
	 * [9]ConcDenomUnit	[10]PRESENTNumQty	[11]PRESENTNumUnit	[12]PRESENTDenomQty	
	 * [13]PRESENTDenomUnit	[14]UoP	[15]DoseForm	Pattern	[16]Status
	 
	protected List<Concept> loadLine(String[] items) throws TermServerScriptException {
		Concept c = gl.getConcept(items[0]);
		c.setConceptType(ConceptType.CLINICAL_DRUG);
		
		//Is this the first time we've seen this concept?
		if (!spreadsheet.containsKey(c)) {
			spreadsheet.put(c, new ArrayList<Ingredient>());
		}
		List<Ingredient> ingredients = spreadsheet.get(c);
		try {
			// Booleans indicate: don't create and do validate that concept exists
			Ingredient ingredient = new Ingredient();
			ingredient.doseForm = getPharmDoseForm(items[15]);
			ingredient.substance = gl.getConcept(items[4], false, true);
			ingredient.boss = gl.getConcept(items[5], false, true);
			ingredient.strength = DrugUtils.getNumberAsConcept(items[6]);
			ingredient.numeratorUnit = DrugUtils.findUnitOfMeasure(items[7]);
			ingredient.denomQuantity = DrugUtils.getNumberAsConcept(items[8]);
			//ingredient.denomUnit = getUnitOfPresentation(items[9]);
			ingredient.denomUnit =  DrugUtils.findUnitOfMeasure(items[9]);
			//ingredient.unitOfPresentation = getUnitOfPresentation(items[14]);
			ingredients.add(ingredient);
		} catch (Exception e) {
			report (null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, e.getMessage());
			return null;
		}
		
		return Collections.singletonList(c);
	}*/

	private Concept getPharmDoseForm(String doseFormStr) throws TermServerScriptException {
		//Do we have an SCTID to work with?  
		Concept doseForm;
		String[] parts = doseFormStr.split(ESCAPED_PIPE);
		if (parts.length > 1) {
			doseForm = gl.getConcept(parts[0].trim(), false, true); //Don't create if not already known
		} else {
			doseForm = DrugUtils.findDoseForm(doseFormStr);
		}
		return doseForm;
	}
	
	private Concept getUnitOfPresentation(String unitPresStr) throws TermServerScriptException {
		//Do we have an SCTID to work with?  
		Concept unitPres;
		String[] parts = unitPresStr.split(ESCAPED_PIPE);
		if (parts.length > 1) {
			unitPres = gl.getConcept(parts[0].trim(), false, true); //Don't create if not already known
		} else {
			unitPres = DrugUtils.findUnitOfPresentation(unitPresStr);
		}
		return unitPres;
	}

	private Concept getAlternative(Task t, Concept c) throws TermServerScriptException {
		//Work through the active historical associations and find an active alternative
		List<HistoricalAssociation> assocs = c.getHistorialAssociations(ActiveState.ACTIVE);
		if (assocs.size() > 1 || assocs.size() == 0) {
			String msg = c + " is inactive with " + assocs.size() + " historical associations.  Cannot determine alternative concept.";
			report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, msg);
			return null;
		}
		Concept refset =  gl.getConcept(assocs.get(0).getRefsetId());
		Concept alternative = gl.getConcept(assocs.get(0).getTargetComponentId());
		alternative.setConceptType(c.getConceptType());
		String msg = "Working on " + alternative + " instead of inactive original " + c + " due to " + refset;
		report (t, c, Severity.MEDIUM, ReportActionType.INFO, msg);
		return alternative;
	}
}
