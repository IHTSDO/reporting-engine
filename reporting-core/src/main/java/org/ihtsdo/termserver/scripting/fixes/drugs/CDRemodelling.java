package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
2018 Work
DRUGS-474 (Pattern 1a) Remodelling of Clinical Drugs including Basis of Strength Substance (BoSS) based on an input spreadsheet
DRUGS-493 (Pattern 1b - ) 
DRUGS-505 (Pattern 1c - Actuation)
DRUGS-504 (Pattern 2a - Infusions)
DRUGS-499 (Pattern 2b - Oral Solutions)
DRUGS-495 (Pattern 3a - Vials)
DRUGS-497 (Pattern 3a - Patches)
DRUGS-496 (Pattern 3b - Creams and Drops)

2019 Work
DRUGS-668 
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CDRemodelling extends DrugBatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(CDRemodelling.class);

	Map<Concept, List<Ingredient>> spreadsheet = new HashMap<>();
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	TermVerifier termVerifier;
	Set<Concept> blackListedConcepts = new HashSet<>();
	Set<Concept> allowIngredientCountChange = new HashSet<>();
	
	//Determine use of presentation / concentration from the Ingredient object
	//boolean usesPresentation = true;   //True for Pattern 1b, 1c, 2a.   False for 2b, 3a.  NB 2a uses both!
	// usesConcentration = false;  //True for liquids eg Pattern 2a, 2b Oral solutions, 3a solutions, 3b - creams.   False for 1c
	//boolean includeUnitOfPresentation = true;  // True for 2a, 1c. False for 3a, 3b
	//boolean specifyDenominator = false;  // True for 2a, 2b, 3a, 3b  False for 1c
	
	boolean cloneAndReplace = true;  //3A Patches being replaced, also 2A Injection Infusions, PWI-Run8
	
	protected CDRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CDRemodelling app = new CDRemodelling(null);
		try {
			ReportSheetManager.setTargetFolderId("1gNnY3XAuopxi5yybh3Kv9P3V2vMMULB0");  // Drugs / Remodeling
			app.expectNullConcepts = true;
			app.inputFileHasHeaderRow = true;
			app.runStandAlone = true;
			
			app.keepIssuesTogether = true;
			
			app.init(args);
			//app.termGenerator.includeUnitOfPresentation(app.includeUnitOfPresentation); 
			//app.termGenerator.specifyDenominator(app.specifyDenominator);
			app.loadProjectSnapshot(false); //Load all descriptions
			app.postInit();
			//We won't include the project export in our timings
			app.processFile();
		} finally {
			app.finish();
		}
	}
	
	@Override
	public void init(String[] args) throws TermServerScriptException {
		super.init(args);
		if (getInputFile(1) == null) {
			LOGGER.warn ("No input file specified to verify terms");
		} else {
			termVerifier = new TermVerifier(getInputFile(1),this);
			termVerifier.init();
		}
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();
		allowIngredientCountChange.add(gl.getConcept("370303000 |Tetracaine hydrochloride 0.3%/dextrose 6% injection solution (product)|"));
		allowIngredientCountChange.add(gl.getConcept("420628003 |Miconazole 0.25%/zinc oxide 15%/white petrolatum 81.35% topical ointment (product)|"));
	}


	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		
		if (blackListedConcepts.contains(concept)) {
			report(task, concept, Severity.NONE, ReportActionType.VALIDATION_CHECK, "Subsequent ingredient failed validation");
			return NO_CHANGES_MADE;
		}
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		boolean cloneThisConcept = cloneAndReplace;
		int changesMade = 0;
		if (loadedConcept.isActive() == false) {
			//Does this concept have an alternative/replacement that we should use instead?
			Concept alternative = getAlternative(task, concept);
			if (alternative == null) {
				cloneThisConcept = true;
			} else {
				task.replace(concept, alternative);
				//We also need a copy of the original's spreadsheet data to look up, and also for the term verifier
				spreadsheet.put(alternative, spreadsheet.get(concept));
				if (termVerifier != null) {
					termVerifier.replace(concept, alternative);
				}
				loadedConcept = loadConcept(alternative, task.getBranchPath());
			}
		}
		changesMade = remodelConcept(task, loadedConcept);
		try {
			changesMade += assignIngredientCounts(task, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Unable to assign ingredient count due to: " + e.getMessage());
		}
		if (changesMade > 0) {
			if (cloneThisConcept) {
				cloneAndReplace(loadedConcept, task, InactivationIndicator.OUTDATED);
			} else {
				updateConcept(task, loadedConcept, info);
			}
		}
		return changesMade;
	}

	private int remodelConcept(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		//The number of ingredients in this drug must match the number we have to model
		Set<Relationship> ingredientRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		List<Ingredient> ingredients = spreadsheet.get(c);
		//Only worry about this if the model has more ingredients than the concept.  Otherwise we'll probably find them in the inferred form, 
		//and if not, we'll throw an error at the time.
		if (ingredientRels.size() > ingredients.size()) {
			if (allowIngredientCountChange.contains(c)) {
				report(t,c,Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Permissable change in ingredient count " + ingredients.size() + " from current " + ingredientRels.size() + " ingredients");
			} else {
				report(t,c,Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Count mismatch in modelled (" + ingredients.size() + ") vs current (" + ingredientRels.size() + ") ingredients");
				return 0;
			}
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
			removeRelationship(t,  c, ai);
		}
		//We're definitely going to have everything we need in the stated form, and in fact the inferred will be wrong because we haven't changed it.
		changesMade += termGenerator.ensureTermsConform(t,c, null, CharacteristicType.STATED_RELATIONSHIP);
		if (termVerifier != null) {
			termVerifier.validateTerms(t, c);
		}
		return changesMade;
	}

	private int remodel(Task t, Concept c, Ingredient modelIngredient, boolean isMultiIngredient) throws TermServerScriptException {
		int changesMade = 0;
		int targetGroupId = SnomedUtils.getFirstFreeGroup(c);
		report(t, c, Severity.NONE, ReportActionType.INFO, "BoSSes: " + c.getIssues(" + "));
		//Find this ingredient.  If it's in group 0, we need to move it to a new group
		Relationship substanceRel = getSubstanceRel(t, c, modelIngredient.substance, modelIngredient.boss);

		//Are we using concentration, presentation or both?
		boolean usesConcentration = modelIngredient.concStrength != null;
		boolean usesPresentation = modelIngredient.presStrength != null;
		
		if (substanceRel == null) {
			//We'll need to create one!
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "No existing ingredient found.  Adding new relationship.");
			substanceRel = new Relationship (c, HAS_PRECISE_INGRED, modelIngredient.substance, targetGroupId);
		} else {
			//If we've found an active ingredient, inactivate it and replace with a PRECISE active ingredient
			if (substanceRel.getType().equals(HAS_ACTIVE_INGRED)) {
				removeRelationship(t, c, substanceRel);
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
			report(t,c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, substanceRel);
			c.addRelationship(substanceRel);
			changesMade++;
		}
		
		//If we're using concentration then remove any relationships for presentation strength
		if (usesConcentration && !usesPresentation) {
			removeRelationships (t, c, HAS_PRES_STRENGTH_VALUE, targetGroupId);
			removeRelationships (t, c, HAS_PRES_STRENGTH_UNIT, targetGroupId);
			removeRelationships (t, c, HAS_PRES_STRENGTH_DENOM_VALUE, targetGroupId);
			removeRelationships (t, c, HAS_PRES_STRENGTH_DENOM_UNIT, targetGroupId);
		}
		
		changesMade += replaceRelationship(t, c, HAS_MANUFACTURED_DOSE_FORM, modelIngredient.doseForm, UNGROUPED, true);
		changesMade += replaceRelationship(t, c, HAS_BOSS, modelIngredient.boss, targetGroupId, false);
		
		if (usesPresentation) {
			changesMade += replaceRelationship(t, c, HAS_PRES_STRENGTH_VALUE, modelIngredient.presStrength, targetGroupId, false);
			changesMade += replaceRelationship(t, c, HAS_PRES_STRENGTH_UNIT , modelIngredient.presNumeratorUnit, targetGroupId, false);
			changesMade += replaceRelationship(t, c, HAS_PRES_STRENGTH_DENOM_VALUE, modelIngredient.presDenomQuantity, targetGroupId, false);
			changesMade += replaceRelationship(t, c, HAS_PRES_STRENGTH_DENOM_UNIT, modelIngredient.presDenomUnit, targetGroupId, false);
		}
		if (usesConcentration) {
			changesMade += replaceRelationship(t, c, HAS_CONC_STRENGTH_VALUE, modelIngredient.concStrength, targetGroupId, false);
			changesMade += replaceRelationship(t, c, HAS_CONC_STRENGTH_UNIT , modelIngredient.concNumeratorUnit, targetGroupId, false);
			changesMade += replaceRelationship(t, c, HAS_CONC_STRENGTH_DENOM_VALUE, modelIngredient.concDenomQuantity, targetGroupId, false);
			changesMade += replaceRelationship(t, c, HAS_CONC_STRENGTH_DENOM_UNIT, modelIngredient.concDenomUnit, targetGroupId, false);
		}
		
		if (modelIngredient.unitOfPresentation != null || termGenerator.includeUnitOfPresentation()) {
			changesMade += replaceRelationship(t, c, HAS_UNIT_OF_PRESENTATION, modelIngredient.unitOfPresentation, UNGROUPED, true);
		}
		return changesMade;
	}

	private Relationship getSubstanceRel(Task t, Concept c, Concept targetSubstance, Concept secondarySubstance) throws TermServerScriptException {
		Set<Relationship> matchingRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, targetSubstance, ActiveState.ACTIVE);
		matchingRels.addAll( c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, targetSubstance, ActiveState.ACTIVE));

		if (matchingRels.size()==0) {
			//We could match an inferred relationship instead
			matchingRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, targetSubstance, ActiveState.ACTIVE);
			matchingRels.addAll( c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, targetSubstance, ActiveState.ACTIVE));
			if (matchingRels.size() > 0) {
				Relationship clone = matchingRels.iterator().next().clone(null);
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
						Relationship clone = matchingRels.iterator().next().clone(null);
						clone.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
						clone.setGroupId(0);  //It'll get moved by the calling code
						return clone;
					}
				}
			}
		}
		
		if (matchingRels.size()!=1) {
			if (matchingRels.size() > 1) {
				report(t,c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to uniquely identify ingredient: " + targetSubstance + ". Found " + matchingRels.size());
			}
			return null;
		}
		return matchingRels.iterator().next();
	}
	

	@Override
	/*
	 * RUN #1 https://docs.google.com/spreadsheets/d/1ZEAce_QljbjfVm3jmhccHT04l58DzbTHZMlWM0-8GPA/edit#gid=913407998
	 * [0]sctid	[1]FSN	[2]sctDoseForm	[3]sctUnitOfPresentation	[4]rxn_BoSS_label	[5]rxn_activeIngredient_label	
	 * [6]numeratorValue	[7]numeratorUnit	[8]denominatorValue	[9]denominatorUnit	[10]Status
	 */
	protected List<Component> loadLine(String[] items) throws TermServerScriptException {
		Concept c = gl.getConcept(items[0]);
		c.setConceptType(ConceptType.CLINICAL_DRUG);
		
		if (c.getConceptId().equals("377496003") || c.getConceptId().equals("377496003") ) {
			LOGGER.debug("Debug here!");
		}
		
		//Is this the first time we've seen this concept?
		if (!spreadsheet.containsKey(c)) {
			spreadsheet.put(c, new ArrayList<Ingredient>());
		}
		
		//Have we seen an ingredient for this concept already and black listed it?
		if (blackListedConcepts.contains(c)) {
			report((Task)null, c, Severity.NONE, ReportActionType.VALIDATION_CHECK, "Subsequent ingredient encountered on previously failed concept");
			return null;
		}
		List<Ingredient> ingredients = spreadsheet.get(c);
		try {
			// Booleans indicate: don't create and do validate that concept exists
			Ingredient ingredient = new Ingredient();
			ingredient.boss = DrugUtils.findSubstance(items[4]);
			ingredient.substance = DrugUtils.findSubstance(items[5]);
			
			if (ingredient.boss == null) {
				report((Task)null, c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "BOSS not identified - " + items[4]);
				blackListedConcepts.add(c);
				return null;
			}
			
			if (ingredient.substance == null) {
				report((Task)null, c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Substance not identified - " + items[5]);
				blackListedConcepts.add(c);
				return null;
			}
			
			boolean usesConcentration = items[10].contains("concentration");
			boolean usesPresentation = items[10].contains("presentation");
			if (usesConcentration == false && usesPresentation == false) {
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to determine conc / pres");
				return null;
			}
			
			StrengthUnit numerator = new StrengthUnit(Double.parseDouble(items[6]), DrugUtils.findUnitOfMeasure(items[7]));
			if (DrugUtils.normalizeStrengthUnit(numerator)) {
				LOGGER.warn ("Normalized Strength of " + c + ": " + numerator);
			}
			
			//Is this a number we have as a concept?
			//N/A Concepts as numbers don't exist anymore
			
			if (usesConcentration) {
				ingredient.concStrength = DrugUtils.getNumberAsConcept(numerator.getStrengthStr());
				ingredient.concNumeratorUnit = numerator.getUnit();
				ingredient.concDenomQuantity = DrugUtils.getNumberAsConcept(items[8]);
				ingredient.concDenomUnit =  DrugUtils.findUnitOfMeasure(items[9]);
			}
			
			if (usesPresentation) {
				ingredient.presStrength = DrugUtils.getNumberAsConcept(numerator.getStrengthStr());
				ingredient.presNumeratorUnit = numerator.getUnit();
				ingredient.presDenomQuantity = DrugUtils.getNumberAsConcept(items[8]);
				ingredient.presDenomUnit =  DrugUtils.findUnitOfMeasure(items[9]);
			}
			
			if (usesPresentation) {
				ingredient.unitOfPresentation = getUnitOfPresentation(items[3]);
			}
			
			ingredient.doseForm = getPharmDoseForm(items[2]);
			ingredients.add(ingredient);
			
			//Set the issue on the concept to ensure they're kept together by BoSS
			c.addIssue(getBossStr(c));
		} catch (Exception e) {
			report((Task)null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, e.getMessage());
			blackListedConcepts.add(c);
			return null;
		}
		return Collections.singletonList(c);
	} 

	private String getBossStr(Concept c) {
		return spreadsheet.get(c).stream()
				.map( i -> i.boss.toString())
				.sorted()
				.collect(Collectors.joining(","));
	}

	private Concept getPharmDoseForm(String doseFormStr) throws TermServerScriptException {
		//Do we have an SCTID to work with?  
		Concept doseForm;
		String[] parts = doseFormStr.split(ESCAPED_PIPE);
		if (parts.length > 1) {
			doseForm = gl.getConcept(parts[0].trim(), false, true); //Don't create if not already known
		} else {
			doseForm = DrugUtils.findDoseFormFromFSN(doseFormStr);
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
		List<AssociationEntry> assocs = c.getAssociationEntries(ActiveState.ACTIVE);
		if (assocs.size() > 1 || assocs.size() == 0) {
			String msg = c + " is inactive with " + assocs.size() + " historical associations.  Cannot determine alternative concept.";
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
			return null;
		}
		Concept refset =  gl.getConcept(assocs.get(0).getRefsetId());
		Concept alternative = gl.getConcept(assocs.get(0).getTargetComponentId());
		alternative.setConceptType(c.getConceptType());
		String msg = "Working on " + alternative + " instead of inactive original " + c + " due to " + refset;
		report(t, c, Severity.MEDIUM, ReportActionType.INFO, msg);
		return alternative;
	}
}
