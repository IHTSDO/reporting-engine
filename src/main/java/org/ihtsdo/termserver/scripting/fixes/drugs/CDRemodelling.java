package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
DRUGS-474 Remodelling of Clinical Drugs including Basis of Strength Substance (BoSS) based on an input spreadsheet
 */
public class CDRemodelling extends DrugBatchFix implements RF2Constants{
	
	Map<Concept, List<Ingredient>> spreadsheet;
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected CDRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CDRemodelling fix = new CDRemodelling(null);
		try {
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't include the project export in our timings
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}


	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (loadedConcept.isActive() == false) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive.  No changes attempted");
		} else {
			changesMade = remodelConcept(task, loadedConcept);
			if (changesMade > 0) {
				try {
					String conceptSerialised = gson.toJson(loadedConcept);
					debug ((dryRun?"Dry run updating":"Updating") + " state of " + loadedConcept + info);
					if (!dryRun) {
						tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
					}
					report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept successfully remodelled. " + changesMade + " changes made.");
				} catch (Exception e) {
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getClass().getSimpleName()  + " - " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
		return changesMade;
	}

	private int remodelConcept(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		//The number of ingredients in this drug must match the number we have to model
		List<Relationship> ingredientRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		List<Ingredient> ingredients = spreadsheet.get(c);
		if (ingredientRels.size() != ingredients.size()) {
			report (t,c,Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Mismatch in modelled vs current ingredients");
			return 0;
		}
		boolean isMultiIngredient = ingredients.size() > 1;
		for (Ingredient ingredient : ingredients) {
			changesMade += remodel(t,c,ingredient, isMultiIngredient);
		}
		changesMade += termGenerator.ensureDrugTermsConform(t,c);
		return changesMade;
	}


	private int remodel(Task t, Concept c, Ingredient modelIngredient, boolean isMultiIngredient) {
		int changesMade = 0;
		int targetGroupId = isMultiIngredient? 0 : SnomedUtils.getFirstFreeGroup(c);
		//Find this ingredient.  If it's in group 0, we need to move it to a new group
		Relationship substanceRel = getSubstanceRel(t, c, modelIngredient.substance);
		if (substanceRel.getGroupId() == 0L) {
			substanceRel.setGroupId(targetGroupId);
		} else if (isMultiIngredient){
			targetGroupId = substanceRel.getGroupId();
		}
		
		changesMade += replaceRelationship(t, c, HAS_MANUFACTURED_DOSE_FORM, modelIngredient.doseForm, UNGROUPED, true);
		changesMade += replaceRelationship(t, c, HAS_BOSS, modelIngredient.boss, targetGroupId, true);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_VALUE, modelIngredient.strength, targetGroupId, true);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_UNIT, modelIngredient.numeratorUnit, targetGroupId, true);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_DENOM_VALUE, modelIngredient.denomQuantity, targetGroupId, true);
		changesMade += replaceRelationship(t, c, HAS_STRENGTH_DENOM_UNIT, modelIngredient.denomUnit, targetGroupId, true);
		changesMade += replaceRelationship(t, c, HAS_UNIT_OF_PRESENTATION, modelIngredient.unitOfPresentation, UNGROUPED, true);
		
		return changesMade;
	}

	private Relationship getSubstanceRel(Task t, Concept c, Concept targetSubstance) {
		List<Relationship> matchingRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, targetSubstance, ActiveState.ACTIVE);
		if (matchingRels.size()!=1) {
			report (t,c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Unable to find expected ingredient: " + targetSubstance);
			return null;
		}
		return matchingRels.get(0);
	}
	
	@Override
	/**
	 * Spreadsheet columns:
	 * [0]conceptId	[1]FSN	[2]Sequence	[3]dose_form_evaluation	[4]pharmaceutical_df	
	 * [5]future_ingredient	[6]future_boss	[7]future_Presentation_num_qty	
	 * [8]future_Presentation_num_unit	[9]future_Presentation_denom_qty	
	 * [10]future_Presentation_denom_unit	[11]future_unit_of_presentation	
	 * [12]dmd_ingredient	[13]stated_ingredient	[14]inferred_ingredient	
	 * [15]dmd_boss	[16]Unit_of_presentation	[17]dmd_numerator_quantity	
	 * [18]dmd_numerator_unit	[19]dmd_denominator_quantity	[20]dmd_denominator_unit	
	 * [21]stated_dose_form	[22]Pattern	[23]Source	[24]status	[25]Comment	[26]Transfer
	 */
	protected Concept loadLine(String[] items) throws TermServerScriptException {
		Concept concept = gl.getConcept(items[0]);
		concept.setConceptType(ConceptType.CLINICAL_DRUG);
		
		//Is this the first time we've seen this concept?
		if (!spreadsheet.containsKey(concept)) {
			spreadsheet.put(concept, new ArrayList<Ingredient>());
		}
		
		Ingredient ingredient = new Ingredient();
		ingredient.presentation = getPharmDoseForm(items[4]);
		ingredient.substance = gl.getConcept(items[5]);
		ingredient.boss = gl.getConcept(items[6]);
		ingredient.strength = getStrength(items[7]);
		ingredient.numeratorUnit = getUnit(items[8]);
		ingredient.denomQuantity = getStrength(items[9]);
		ingredient.denomUnit =  getUnit(items[10]);
		
		return concept;
	}

	private Concept getUnit(String unit) throws TermServerScriptException {
		Concept c = DrugUtils.findUnit(unit);
		if (c == null) {
			warn("Unable to find unit concept for: " + unit);
		}
		return c;
	}

	private Concept getStrength(String strength) throws TermServerScriptException {
		Concept c = DrugUtils.getNumberAsConcept(strength);
		if (c == null) {
			warn("Unable to find number concept to represent: " + strength);
		}
		return c;
	}

	private Concept getPharmDoseForm(String doseFormStr) throws TermServerScriptException {
		//Do we have an SCTID to work with?  
		Concept doseForm;
		String[] parts = doseFormStr.split(ESCAPED_PIPE);
		if (parts.length > 1) {
			doseForm = gl.getConcept(parts[0].trim());
		} else {
			doseForm = DrugUtils.findDoseForm(doseFormStr);
		}
		return doseForm;
	}

	/*	private int remodelAttributes(Task task, Concept remodelledConcept, Concept tsConcept) throws ValidationFailure {
	//Inactivate all stated relationships unless they're one of the ones we want to add
	int changesMade = 0;
	if (remodelledConcept.getRelationships() == null || remodelledConcept.getRelationships().isEmpty()) {
		throw new ValidationFailure(tsConcept, Severity.LOW, ReportActionType.VALIDATION_ERROR, "No relationships found to remodel concept. Out of scope? Skipping.");
	} else {
		for (Relationship r : tsConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			//Leave existing parents as they are.  Definition status of concept will also not change.
			if (r.getType().equals(IS_A)) {
				continue;
			}
			
			if (r.getType().getConceptId().equals(SCTID_HAS_ACTIVE_INGREDIENT)) {
				//Check the ingredient FSN appears in whole in the product's fsn
				String ingredient = SnomedUtils.deconstructFSN(r.getTarget().getFsn())[0].toLowerCase();
				if (!remodelledConcept.getFsn().toLowerCase().contains(ingredient)) {
					report(task, tsConcept, Severity.MEDIUM, ReportActionType.VALIDATION_ERROR, "New FSN does not contain active ingredient: " + ingredient + " - " + remodelledConcept.getFsn());
				}
			}
			
			if (remodelledConcept.getRelationships().contains(r)) {
				remodelledConcept.getRelationships().remove(r);
				//TODO Check for existing inactive relationships and reactivate
			} else {
				r.setActive(false);
				changesMade++;
			}
		}
		//Now loop through whatever relationships we have left and add them to the ts concept
		for (Relationship r : remodelledConcept.getRelationships()) {
			if (r.getType().isActive() == false || r.getTarget().isActive() == false) {
				report(task, tsConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Unable to add relationship, inactive type or destination: " + r);
			} else {
				tsConcept.addRelationship(r);
				changesMade++;
			}
		}
	}
	return changesMade;
	}
	
	private int remodelDescriptions(Task task, Concept remodelledConcept,
			Concept tsConcept) throws TermServerScriptException {
		ConceptChange change = (ConceptChange)remodelledConcept;
		int changesMade = 0;
		if (!change.getCurrentTerm().equals(tsConcept.getFsn())) {
			report(task, tsConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Existing FSN did not meet change input file expectations: " + change.getFsn());
		} else {
			//Firstly, inactivate and replace the FSN
			Description fsn = tsConcept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE).get(0);
			Description replacement = fsn.clone(null);
			replacement.setTerm(change.getFsn());
			replacement.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
			replacement.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(Acceptability.PREFERRED, ENGLISH_DIALECTS));
			fsn.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			tsConcept.addDescription(replacement);
			changesMade++;
			
			//Now synonyms
			changesMade += remodelSynonyms(task, remodelledConcept, tsConcept);
		}
		return changesMade;
	}
	
	private int remodelSynonyms(Task task, Concept remodelledConcept,
			Concept tsConcept) {
		int changesMade = 0;
		//Now make all synonyms unacceptable, unless we're keeping them
		for (Description d : tsConcept.getDescriptions(ActiveState.ACTIVE)) {
			Description keeping = findDescription (remodelledConcept, d.getTerm());
			if (keeping!=null) {
				if (!d.isActive()) {
					report(task, tsConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Inactivated description being made active");
					d.setActive(true);
				}
				//TODO Check acceptability of existing term
				//And remove the description from our change set, so we don't add it again
				remodelledConcept.getDescriptions().remove(keeping);
			} else {
				//VMP concepts are going to retain their existing preferred terms
				if (mode != Mode.VMP || !d.isPreferred()) {
					d.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					changesMade++;
				} else {
					//Existing preferred terms are being set to case-insensitive
					d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
					changesMade++;
				}
				
				if (isCaseSensitive(d)) {
					report (task, tsConcept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Existing description was case sensitive : " + d);
				}
			}
		}
		//Add back in any remaining descriptions from our change set
		for (Description newDesc : remodelledConcept.getDescriptions()) {
			tsConcept.addDescription(newDesc);
			changesMade++;
		}
		return changesMade;
	}*/

}
