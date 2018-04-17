package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
			int changes = assignIngredientCounts(task, loadedConcept);
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

	private int assignIngredientCounts(Task t, Concept c) throws TermServerScriptException {
		int changes = 0;
		Set<Concept> ingredients = DrugUtils.getIngredients(c);
		if (ingredients.size() == 1) {
			changes = replaceRelationship(t, c, COUNT_BASE_ACTIVE_INGREDIENT, DrugUtils.getNumberAsConcept("1"), UNGROUPED, true);
		} else {
			//Quick check that the number of ingredients matches the number of " and "
			if (ingredients.size() != c.getFsn().split(AND).length) {
				report(t,c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN does not suggest " + ingredients.size() + " ingredients");
			}
			Set<Concept> bases = getBases(ingredients);
			if (bases.size() != ingredients.size()) {
				debug ("SpotCheck: " + c + " - " + ingredients.size() + "/" + bases.size());
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
			bases.add(getBase(ingredient));
		}
		return bases;
	}

	private Concept getBase(Concept c) {
		List<Concept> bases = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
				.stream()
				.map(rel -> rel.getTarget())
				.collect(Collectors.toList());
		if (bases.size() == 0) {
			return c;
		} else if (bases.size() > 1) {
			throw new IllegalArgumentException("Concept " + c + " has multiple modification attributes");
		} else if (bases.get(0).equals(c)) {
			throw new IllegalArgumentException("Concept " + c + " is a modification of itself.");
		} else  {
			//Call recursively to follow the transitive nature of modification
			return getBase(bases.get(0));
		}
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
				default:
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return asComponents(processMe);
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
}
