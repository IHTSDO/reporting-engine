package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugUtils;

import us.monoid.json.JSONObject;

/*
For DRUGS-482
Add ingredient counts where required.  Algorithm described in DRUGS-476.
*/
public class IngredientCounts extends DrugBatchFix implements RF2Constants{
	
	protected IngredientCounts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		IngredientCounts fix = new IngredientCounts(null);
		try {
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		
		try {
			int changes = assignIngredientCounts(task, loadedConcept, CharacteristicType.INFERRED_RELATIONSHIP);
			if (changes > 0) {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			}
			return changes;
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return NO_CHANGES_MADE;
	}

	public int assignIngredientCounts(Task t, Concept c, CharacteristicType charType) throws TermServerScriptException {
		int changes = 0;
		Set<Concept> ingredients = DrugUtils.getIngredients(c, charType);
		if (ingredients.size() == 1) {
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT, DrugUtils.getNumberAsConcept("1"), UNGROUPED, true);
		} else {
			//Quick check that the number of ingredients matches the number of " and "
			if (ingredients.size() != c.getFsn().split(AND).length) {
				report(t,c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN does not suggest " + ingredients.size() + " ingredients");
			}
			Set<Concept> bases = getBases(ingredients);
			if (bases.size() != ingredients.size()) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Ingredients / Base Count: " + ingredients.size() + " / " + bases.size());
			}
			Concept baseCountConcept = DrugUtils.getNumberAsConcept(Integer.toString(bases.size()));
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT, baseCountConcept, UNGROUPED, true);
		}
		return changes;
	}
	
	private Set<Concept> getBases(Set<Concept> ingredients) throws TermServerScriptException {
		Set<Concept> bases = new HashSet<>();
		for (Concept ingredient : ingredients) {
			//We need a local copy of the ingredient to get it's full set of relationship concepts
			ingredient = gl.getConcept(ingredient.getConceptId());
			bases.add(DrugUtils.getBase(ingredient));
		}
		return bases;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//List<Concept> processMe = new ArrayList<>(Collections.singletonList(gl.getConcept("377209001")));
		List<Concept> processMe = new ArrayList<>();
		for (Concept drug : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
			DrugUtils.setConceptType(drug);
			switch (drug.getConceptType()) {
				case CLINICAL_DRUG: if (drug.getFsn().contains("precisely")) {
										processMe.add(drug);
				}
					break;
				case MEDICINAL_PRODUCT:
				case MEDICINAL_PRODUCT_FORM: if (drug.getFsn().contains("only")) {
					processMe.add(drug);
				}
					break;
				default:
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return asComponents(processMe);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
}
