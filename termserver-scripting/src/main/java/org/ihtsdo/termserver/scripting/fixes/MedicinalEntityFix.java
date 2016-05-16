package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import us.monoid.json.JSONException;
import us.monoid.web.JSONResource;

/*
All concepts must be fully defined
All concepts must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts must be 373873005| Pharmaceutical / biologic product (product).
All concepts must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
All concepts in the module must have one and only one Has dose form attribute when "Product Strength" or "Medicinal Form"
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).
Any plus symbol in the name must be surrounded by single space, exclude for "Product Strength"
Ingredients in name should be in alpha order (Change amounts order to match).
Remove the word "preparation" also "product" when not part of semantic tag 
Change m/r to modified-release 
2nd ingredient should be lower case 

Medicinal Entity plus all descendants in one task, group by "has active ingredient"
 */
public class MedicinalEntityFix extends BatchFix implements RF2Constants{
	
	public static void main(String[] args) throws TermServerFixException, IOException {
		MedicinalEntityFix fix = new MedicinalEntityFix();
		fix.init(args);
		fix.processFile();
	}

	@Override
	public void doFix(Concept concept, String branchPath) {
		debug ("Examining: " + concept.getConceptId());
		ensureDefinitionStatus(concept, DEFINITION_STATUS.FULLY_DEFINED);
		ensureAcceptableParent(concept, PHARM_BIO_PRODUCT);
	}


	@Override
	List<Batch> formIntoBatches(String fileName, List<Concept> concepts, String branchPath) throws TermServerFixException {
		//Medicinal Entity a little tricky.  We're going to recover all the concepts
		//then work out which ones have the same set of active ingredients 
		//and batch those together.
		List<Batch> batches = new ArrayList<Batch>();
		Multimap<String, Concept> ingredientCombos = ArrayListMultimap.create();
		for (Concept thisConcept : concepts) {
			Concept loadedConcept = loadConcept(thisConcept, branchPath);
			//Work out a unique key by the concatenation of all inferred active ingredients
			List<Relationship> ingredients = loadedConcept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED);
			String comboKey = getIngredientCombinationKey(loadedConcept, ingredients);
			ingredientCombos.put(comboKey, loadedConcept);
		}
		//Now each of those concepts that shares that combination of ingredients can go into the same batch
		for (String thisComboKey : ingredientCombos.keySet()) {
			Collection<Concept> conceptsWithCombo = ingredientCombos.get(thisComboKey);
			Batch batchThisCombo = new Batch();
			batchThisCombo.setDescription(fileName + ": " + thisComboKey);
			batchThisCombo.setConcepts(new ArrayList<Concept>(conceptsWithCombo));
			print ("Batched " + conceptsWithCombo.size() + " concepts with ingredients " + thisComboKey);
			batches.add(batchThisCombo);
		}
		return batches;
	}

	private String getIngredientCombinationKey(Concept loadedConcept, List<Relationship> ingredients) throws TermServerFixException {
		String comboKey = "";
		for (Relationship r : ingredients) {
			comboKey = r.getTarget().getConceptId() + "_";
		}
		if (comboKey.isEmpty()) {
			throw new TermServerFixException("Unable to find any ingredients for: " + loadedConcept);
		}
		return comboKey;
	}

}
