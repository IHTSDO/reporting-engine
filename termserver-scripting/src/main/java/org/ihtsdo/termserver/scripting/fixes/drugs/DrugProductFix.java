package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

import com.b2international.commons.StringUtils;

/*
Drug Product fix loads the input file, but only really works with the Medicinal Entities.
The full project hierarchy is exported and loaded locally so we can scan for other concepts
that have the exact same combination of ingredients.
These same ingredient concepts are worked on together in a single task.
What rules are applied to each one depends on the type - Medicinal Entity, Product Strength, Medicinal Form
 */
public class DrugProductFix extends BatchFix implements RF2Constants{
	
	String [] unwantedWords = new String[] { "preparation", "product" };
	
	static Map<String, String> wordSubstitution = new HashMap<String, String>();
	static {
		wordSubstitution.put("acetaminophen", "paracetamol");
	}
	
	public DrugProductFix(BatchFix clone) {
		super(clone);
	}

	private static final String SEPARATOR = "_";
	
	ProductStrengthFix psf;
	MedicinalFormFix mff;
	MedicinalEntityFix mef;
	GrouperFix gf;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		DrugProductFix fix = new DrugProductFix(null);
		try {
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true);  //Load FSNs only
			//We won't incude the project export in our timings
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		psf = new ProductStrengthFix(this);
		mff = new MedicinalFormFix(this);
		mef = new MedicinalEntityFix(this);
		gf =  new GrouperFix(this);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if (concept.getConceptType().equals(ConceptType.UNKNOWN)) {
			determineConceptType(concept);
		}
		loadedConcept.setConceptType(concept.getConceptType());
		int changesMade = 0;
		
		switch (concept.getConceptType()) {
			case MEDICINAL_ENTITY : changesMade = mef.doFix(task, loadedConcept, info);
									break;
			case MEDICINAL_FORM : changesMade = mff.doFix(task, loadedConcept, info);
									break;
			case PRODUCT_STRENGTH : changesMade = psf.doFix(task, loadedConcept, info);
									break;
			case GROUPER :			//No fixes being made to groupers for now
									//changesMade = gf.doFix(batch, loadedConcept);
									break;
			case PRODUCT_ROLE : 
			default : warn ("Don't know what to do with " + concept);
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept Type not determined.");
		}
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ("Updating state of " + loadedConcept + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
		}
		return changesMade;
	}


	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> conceptsInFile, String branchPath) throws TermServerScriptException {
		debug ("Finding all concepts with ingredients...");
		List<Concept> allConceptsBeingProcessed = new ArrayList<Concept>();
		Batch batch =  createBatch(fileName, conceptsInFile, allConceptsBeingProcessed);
		//Did we end up with small tasks that can be merged with larger ones? 
		mergeSmallTasks(batch);
		listTaskSizes(batch);
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConceptsBeingProcessed);
		List <Concept> reportedNotProcessed = validateAllInputConceptsBatched (conceptsInFile, allConceptsBeingProcessed);
		addSummaryInformation(REPORTED_NOT_PROCESSED, reportedNotProcessed);
		storeRemainder(CONCEPTS_IN_FILE, CONCEPTS_PROCESSED, REPORTED_NOT_PROCESSED, "Gone Missing");
		return batch;
	}
	
/*  We're no longer looking for all concepts with the same ingredients, instead we'll just process 
	the concepts in the file.
	Batch createBatch(String fileName, List<Concept> conceptsInFile, Multimap<String, Concept> ingredientCombos, List<Concept> allConceptsBeingProcessed, List<String> ingredientCombosBeingProcessed) throws TermServerFixException {
		Batch batch = new Batch(fileName);
		List<List<Concept>> groupedConcepts = separateOutSingleIngredients(conceptsInFile);
		//If the concept is of type Medicinal Entity, then put it in a batch with other concept with same ingredient combo
		boolean startNewTask = false; //We'll start a new task when we switch from single to multiple ingredients
		for (List<Concept> theseConcepts : groupedConcepts) {
			for (Concept thisConcept : theseConcepts) {
				if (thisConcept.getConceptType().equals(ConceptType.MEDICINAL_ENTITY)) {
					//Add all concepts with this ingredient combination to the list of concepts to be processed
					List<Relationship> ingredients = getIngredients(thisConcept);
					String thisComboKey = getIngredientCombinationKey(thisConcept, ingredients);
					Collection<Concept> sameIngredientConcepts = ingredientCombos.get(thisComboKey);
					allConceptsBeingProcessed.addAll(sameIngredientConcepts);
					ingredientCombosBeingProcessed.add(thisComboKey);
					splitConceptsIntoTasks(batch, sameIngredientConcepts, thisConcept, startNewTask);
					startNewTask = false;
				} else {
					//Validate that concept does have a type and some ingredients otherwise it's going to get missed
					if (thisConcept.getConceptType().equals(ConceptType.UNKNOWN)) {
						warn ("Concept is of unknown type: " + thisConcept);
					}
					
					if (getIngredients(thisConcept).size() == 0) {
						warn ("Concept has no ingredients: " + thisConcept);
					}
				}
			}
			startNewTask = true;
		}
		return batch;
	}*/
	
	private void listTaskSizes(Batch batch) {
		for (Task thisTask : batch.getTasks()) {
			debug (thisTask + " size = " + thisTask.size());
		}
	}

	private void mergeSmallTasks(Batch batch) {
		//Order the tasks by size and put the smallest tasks into the largest ones with space
		List<Task> smallToLarge = orderTasks(batch, true);
		nextSmallTask:
		for (Task thisSmallTask : smallToLarge) {
			if (thisSmallTask.size() < taskSize) {
				for (Task thisLargeTask : orderTasks(batch, false)) {
					if (thisLargeTask.size() + thisSmallTask.size() <= taskSize + wiggleRoom && !thisLargeTask.equals(thisSmallTask)) {
						debug ("Merging task " + thisLargeTask + " (" + thisLargeTask.size() + ") with " + thisSmallTask + " (" + thisSmallTask.size() + ")");
						batch.merge(thisLargeTask, thisSmallTask);
						continue nextSmallTask;
					}
				}
			}
		}
		
	}
	
	List<Task> orderTasks(Batch batch, boolean ascending) {
		List<Task> orderedList = (List<Task>)batch.getTasks().clone();
		Collections.sort(orderedList, new Comparator<Task>() 
		{
			public int compare(Task t1, Task t2) 
			{
				return ((Integer)(t1.size())).compareTo((Integer)(t2.size()));
			}
		});
		if (!ascending) {
			Collections.reverse(orderedList);
		}
		return orderedList;
	}

	Batch createBatch(String fileName, List<Concept> conceptsInFile, List<Concept> allConceptsBeingProcessed ) throws TermServerScriptException {
		Batch batch = new Batch(fileName);
		Set<Set<Concept>> groupedConcepts = groupByIngredients(conceptsInFile);
		Task t = batch.addNewTask();
		for (Set<Concept> theseConcepts : groupedConcepts) {
			//Do we need to start a new task?
			if (t.size() > 0 && t.size() + theseConcepts.size() > taskSize) {
				t = batch.addNewTask();
			}
			
			List<List<Concept>> groupedBySingle = separateOutSingleIngredients(theseConcepts); 
			//If there are only 2 single or 2 multiple concepts, then don't bother splitting.  Better to avoid merge issues.
			if (theseConcepts.size() > taskSize + wiggleRoom && groupedBySingle.get(0).size() > 2 && groupedBySingle.get(1).size() > 2) {
				//Split into single and multiple ingredients
				boolean isSingle = true;
				for (List<Concept> thisGroup : groupedBySingle) {
					if (isSingle) {
						isSingle = false;
					} else {
						t = batch.addNewTask();
					}
					for (Concept thisConcept : thisGroup) {
						t.add(thisConcept);
						allConceptsBeingProcessed.add(thisConcept);
					}
				}
			} else {
				for (Concept thisConcept : theseConcepts) {
					t.add(thisConcept);
					allConceptsBeingProcessed.add(thisConcept);
				}
			}
		}
		return batch;
	}
	
	private List<List<Concept>> separateOutSingleIngredients(
			Set<Concept> concepts) {
		//Group concepts by whether or not they have single or multiple ingredients
		List<List<Concept>> groupedConcepts = new ArrayList<List<Concept>>();
		groupedConcepts.add(new ArrayList<Concept>());
		groupedConcepts.add(new ArrayList<Concept>());
		for (Concept thisConcept : concepts) {
			if (getIngredients(thisConcept).size() == 1) {
				groupedConcepts.get(0).add(thisConcept);
			} else {
				groupedConcepts.get(1).add(thisConcept);
			}
		}
		return groupedConcepts;
	}

	private Set<Set<Concept>> groupByIngredients(List<Concept> conceptsInFile) throws TermServerScriptException {
		//We can find the multiple ingredients first, and then try and put common ingredients
		//in the same task.
		Set<Set<Concept>> groupedByIngredients = new HashSet<Set<Concept>>();
		Set<Concept> remaining = new HashSet<Concept>(conceptsInFile);
		Set<String> multipleIngredients = extractMultipleIngredients(conceptsInFile);
		Map<String, List<Concept>> conceptsByIngredient = getConceptsByIngredient(conceptsInFile);
		//Now what multiple ingredients have ingredients in common?
		Set<Set<String>> ingredientsInCommon = findIngredientsInCommon(multipleIngredients);
		for (Set<String> theseIngredientsInCommon : ingredientsInCommon) {
			Set<Concept> group = new HashSet<Concept>();
			for (String thisCombo : theseIngredientsInCommon) {
				for (String thisIngredient : thisCombo.split("_")) {
					List<Concept> matchingConcepts = conceptsByIngredient.get(thisIngredient);
					for (Concept thisConcept : matchingConcepts) {
						if (remaining.contains(thisConcept)) {
							group.add(thisConcept);
							remaining.remove(thisConcept);
						}
					}
				}
			}
			if (group.size()>0) {
				groupedByIngredients.add(group);
			}
		}
		//Now a group for each remaining ingredient
		for (String thisIngredient : conceptsByIngredient.keySet()) {
			Set<Concept> group = new HashSet<Concept>();
			for (Concept thisConcept : conceptsByIngredient.get(thisIngredient)) {
				if (remaining.contains(thisConcept)) {
					group.add(thisConcept);
					remaining.remove(thisConcept);
				}
			}
			if (group.size() > 0) {
				groupedByIngredients.add(group);
			}
		}
		return groupedByIngredients;
	}

	private Set<Set<String>> findIngredientsInCommon(Set<String> multipleIngredients) {
		Set<Set<String>> ingredientsInCommon = new HashSet<Set<String>>();
		Set<String> remaining = new HashSet<String>(multipleIngredients);
		for (String thisCombo : multipleIngredients) {
			Set<String> commonSet = new HashSet<String>();
			remaining.remove(thisCombo);
			for (String thisIngredient : thisCombo.split("_")) {
				for (String thisRemainingCombo : remaining) {
					if (thisRemainingCombo.contains(thisIngredient)) {
						commonSet.add(thisRemainingCombo);
						commonSet.add(thisCombo);
					}
				}
			}
			if (commonSet.size() > 0) {
				ingredientsInCommon.add(commonSet);
			}
		}
		return ingredientsInCommon;
	}

	private Set<String> extractMultipleIngredients(List<Concept> concepts) throws TermServerScriptException {
		Set<String> multipleIngredients = new HashSet<String>();
		for (Concept thisConcept : concepts) {
			List<Relationship> ingredients = thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
			if (ingredients.size() > 0) {
				String comboKey = getIngredientCombinationKey(thisConcept, ingredients);
				multipleIngredients.add(comboKey);
			}
		}
		return multipleIngredients;
	}
	

	private Map<String, List<Concept>> getConceptsByIngredient(
			List<Concept> concepts) {
		Map<String, List<Concept>> conceptsByIngredient = new HashMap<String, List<Concept>>();
		for (Concept thisConcept: concepts) {
			for (Relationship thisIngredient : getIngredients(thisConcept)) {
				String thisIngredientId = thisIngredient.getTarget().getConceptId();
				if (!conceptsByIngredient.containsKey(thisIngredientId)) {
					conceptsByIngredient.put (thisIngredientId, new ArrayList<Concept>());
				}
				conceptsByIngredient.get(thisIngredientId).add(thisConcept);
			}
		}
		return conceptsByIngredient;
	}


	private List<Concept> validateAllInputConceptsBatched(List<Concept> concepts,
			List<Concept> allConceptsToBeProcessed) {
		List<Concept> reportedNotProcessed = new ArrayList<Concept>();
		//Ensure that all concepts we got given to process were captured in one batch or another
		for (Concept thisConcept : concepts) {
			if (!allConceptsToBeProcessed.contains(thisConcept) && !thisConcept.getConceptType().equals(ConceptType.GROUPER)) {
				reportedNotProcessed.add(thisConcept);
				String msg = thisConcept + " was given in input file but did not get included in a batch.  Check active ingredient.";
				report(null, thisConcept, Severity.CRITICAL, ReportActionType.UNEXPECTED_CONDITION, msg);
			}
		}
		println("Processing " + allConceptsToBeProcessed.size() + " concepts.");
		return reportedNotProcessed;
	}

	private List<Relationship> getIngredients(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
	}
	
	/*private String getIngredientList(List<Relationship> ingredientRelationships) {
		ArrayList<String> ingredientNames = new ArrayList<String>();
		for (Relationship r : ingredientRelationships) {
			String ingredientName = r.getTarget().getFsn().replaceAll("\\(.*?\\)","").trim().toLowerCase();
			ingredientName = SnomedUtils.substitute(ingredientName, wordSubstitution);
			ingredientNames.add(ingredientName);
		}
		Collections.sort(ingredientNames);
		String list = ingredientNames.toString().replaceAll("\\[|\\]", "").replaceAll(", "," + ");
		return SnomedUtils.capitalize(list);
	}*/
	
/*	private Multimap<String, Concept> findAllIngredientCombos() throws TermServerScriptException {
		Collection<Concept> allConcepts = GraphLoader.getGraphLoader().getAllConcepts();
		Multimap<String, Concept> ingredientCombos = ArrayListMultimap.create();
		for (Concept thisConcept : allConcepts) {
			List<Relationship> ingredients = thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
			if (ingredients.size() > 0) {
				String comboKey = getIngredientCombinationKey(thisConcept, ingredients);
				ingredientCombos.put(comboKey, thisConcept);
			}
		}
		return ingredientCombos;
	}*/

	private String getIngredientCombinationKey(Concept loadedConcept, List<Relationship> ingredients) throws TermServerScriptException {
		String comboKey = "";
		Collections.sort(ingredients);  //Ingredient order must be consistent.
		for (Relationship r : ingredients) {
			if (r.isActive()) {
				comboKey += r.getTarget().getConceptId() + SEPARATOR;
			}
		}
		if (comboKey.isEmpty()) {
			println ("*** Unable to find ingredients for " + loadedConcept);
			comboKey = "NONE";
		}
		return comboKey;
	}

	@Override
	public String getScriptName() {
		return "MedicinalEntity";
	}
	

	protected int ensureAcceptableFSN(Task task, Concept concept, Map<String, String> wordSubstitution) throws TermServerScriptException {
		String[] fsnParts = SnomedUtils.deconstructFSN(concept.getFsn());
		String newFSN = removeUnwantedWords(task, concept, fsnParts[0]);
		int changesMade = 0;
		boolean isMultiIngredient = fsnParts[0].contains(INGREDIENT_SEPARATOR);
		if (isMultiIngredient) {
			newFSN = normalizeMultiIngredientTerm(newFSN);
		}

		if (wordSubstitution != null) {
			newFSN = doWordSubstitution(task, concept, newFSN, wordSubstitution);
		}
		//have we changed the FSN?  Reflect that in the Preferred Term(s) if so
		if (!newFSN.equals(fsnParts[0])) {
			updateFsnAndPrefTerms(task, concept, newFSN, fsnParts[1]);
			changesMade = 1;
		}
		return changesMade;
	}

	private String doWordSubstitution(Task task, Concept concept,
			String newFSN, Map<String, String> wordSubstitution) {

		String modifiedFSN = SnomedUtils.substitute(newFSN, wordSubstitution);
		if (!modifiedFSN.equals(newFSN)) {
			String msg = "Word substitution changed " + newFSN + " to " + modifiedFSN;
			report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
			newFSN = modifiedFSN;
		}
		return newFSN;
	}

	/*
	If the FSN contains acetaminophen:
	Inactivate the FSN and create a new FSN changing acetaminophen to paracetamol and applying the usual rules eg: alpha order, spaces around +
	Concept should have one PT for US/GB corresponding to FSN (description may already exist or may need to be created)
	Change any PT containing acetaminophen to a synonym with US=A, GB=N and no changes eg: alpha order, spaces around +
	 */
	private void updateFsnAndPrefTerms(Task task, Concept concept,
			String newFSN, String semanticTag) throws TermServerScriptException {
		String fullFSN = newFSN + SPACE + semanticTag;
		concept.setFsn(fullFSN);
		//FSNs are also preferred so we can just replace all preferred terms
		List<Description> fsnAndPreferred = concept.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE);
		for (Description thisDescription : fsnAndPreferred) {
			Description replacement = thisDescription.clone(null);
			thisDescription.setActive(false);
			thisDescription.setEffectiveTime(null);
			thisDescription.setInactivationIndicator(InactivationIndicator.RETIRED);
			if (thisDescription.getType().equals(DescriptionType.FSN)) {
				replacement.setTerm(fullFSN);
			} else {
				if (attemptAcceptableSYNPromotion(task, concept, newFSN, thisDescription)) {
					replacement = null;
				} else {
					replacement.setTerm(newFSN);
				}
				
				if (checkForDemotion(thisDescription, newFSN)) {
					String msg = "Demoted " + thisDescription + " to  " + SnomedUtils.toString(thisDescription.getAcceptabilityMap());
					report (task, concept, Severity.HIGH, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
			
			if (replacement != null) {
				//Check to see if we're adding another description when we could better just increase
				//the Acceptability of an existing preferred term
				String msg;
				Description improvedAcceptablity = attemptAcceptabilityImprovement(replacement, concept);
				if (improvedAcceptablity != null) {
					msg = "Improved Acceptability of existing term: " + improvedAcceptablity + " now " + SnomedUtils.toString(improvedAcceptablity.getAcceptabilityMap());
				} else {
					concept.addDescription(replacement);
					msg = "Replaced (inactivated) " + thisDescription.getType() + " " + thisDescription + " with " + replacement;
				}
				report (task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
				
			}
		}
		
	}

	private Description attemptAcceptabilityImprovement(
			Description replacement, Concept concept) throws TermServerScriptException {
		//Look through all exising Preferred Terms to find if there's an existing one that matches
		//which could have it's Acceptability improved instead of adding the replacement
		List<Description> preferredTerms = concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.BOTH);
		Description improvedDescription = null;
		for (Description desc : preferredTerms) {
			if (desc.getTerm().equals(replacement.getTerm())) {
				int existingScore = SnomedUtils.accetabilityScore(desc.getAcceptabilityMap());
				Map<String, Acceptability> mergedMap = SnomedUtils.mergeAcceptabilityMap(desc.getAcceptabilityMap(), replacement.getAcceptabilityMap());
				int newScore = SnomedUtils.accetabilityScore(mergedMap);
				if (newScore > existingScore) {
					improvedDescription = desc;
					desc.setAcceptabilityMap(mergedMap);
					desc.setActive(true);
				}
			}
		}
		return improvedDescription;
	}

	private boolean checkForDemotion(Description originalDesc, String newFSN) {
		boolean demotionPerformed = false;
		//Normalise the original Description to see if the ingredients look like they've changed
		String sanitizedTerm = removeUnwantedWords(originalDesc.getTerm());
		String origDescNorm = normalizeMultiIngredientTerm(sanitizedTerm);
		boolean isAcetaminophen = origDescNorm.toLowerCase().contains(ACETAMINOPHEN);
		if (!origDescNorm.equals(newFSN)) {
			//Demote the original description rather than inactivating it
			originalDesc.setActive(true);
			for (String dialect : originalDesc.getAcceptabilityMap().keySet()) {
				originalDesc.getAcceptabilityMap().put(dialect, Acceptability.ACCEPTABLE);
			}
			if (isAcetaminophen) {
				originalDesc.getAcceptabilityMap().remove(GB_ENG_LANG_REFSET);
			}
			demotionPerformed = true;
		}
		return demotionPerformed;
	}

	private boolean attemptAcceptableSYNPromotion(Task task, Concept concept,
			String newTerm, Description oldDescription) throws TermServerScriptException {
		//If we have a term which is only Acceptable (ie not preferred in either dialect)
		//then promote it to Preferred in the appropriate dialect
		boolean promotionSuccessful = false;
		List<Description> allAcceptable = concept.getDescriptions(Acceptability.ACCEPTABLE, DescriptionType.SYNONYM, ActiveState.BOTH);
		List<Description> matchingAcceptable = new ArrayList<Description>();
		for (Description thisDesc : allAcceptable) {
			if (thisDesc.getTerm().equals(newTerm)) {
				matchingAcceptable.add(thisDesc);
			}
		}
		
		if (matchingAcceptable.size() > 1) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "More than one possible description promotion detected.");
		}
		
		if (matchingAcceptable.size() > 0) {
			Description promoting = matchingAcceptable.get(0);
			promoting.setActive(true);
			//Now find the dialects that were preferred in the old term and copy to new
			for (Map.Entry<String, Acceptability> acceptablityEntry : oldDescription.getAcceptabilityMap().entrySet()) {
				String dialect = acceptablityEntry.getKey();
				Acceptability a = acceptablityEntry.getValue();
				if (a.equals(Acceptability.PREFERRED)) {
					promoting.getAcceptabilityMap().put(dialect, Acceptability.PREFERRED);
					promotionSuccessful = true;
					String msg = "Promoted acceptable term " + promoting + " to be preferred in dialect " + dialect;
					report (task, concept, Severity.HIGH, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
		}
		
		return promotionSuccessful;
	}

	protected String normalizeMultiIngredientTerm(String term) {
		//Terms like Oral form, Topical form, Vaginal form should not be moved around 
		//as these are not ingredients.  Return unchanged.
		if (term.contains(" form")) {
			debug ("Skipping normalization of term " + term);
			return term;
		}
		
		String[] ingredients = term.split(INGREDIENT_SEPARATOR_ESCAPED);
		//ingredients should be in alphabetical order, also trim spaces
		for (int i = 0; i < ingredients.length; i++) {
			ingredients[i] = ingredients[i].toLowerCase().trim();
		}
		Arrays.sort(ingredients);

		//Reform with spaces around + sign and only first letter capitalized
		boolean isFirstIngredient = true;
		term = "";
		for (String thisIngredient : ingredients) {
			if (!isFirstIngredient) {
				term += SPACE + INGREDIENT_SEPARATOR + SPACE;
			} 
			term += thisIngredient.toLowerCase();
			isFirstIngredient = false;
		}
		return StringUtils.capitalizeFirstLetter(term);
	}

	private String removeUnwantedWords(String str) {
		for (String unwantedWord : unwantedWords) {
			String[] unwantedWordCombinations = new String[] { SPACE + unwantedWord, unwantedWord + SPACE };
			for (String thisUnwantedWord : unwantedWordCombinations) {
				if (str.contains(thisUnwantedWord)) {
					str = str.replace(thisUnwantedWord,"");
				}
			}
			
		}
		return str;
	}
	
	private String removeUnwantedWords(Task task, Concept concept,
			String fsnRoot) {
		String sanitizedFsnRoot = removeUnwantedWords(fsnRoot);
		if (!sanitizedFsnRoot.equals(fsnRoot)) {
			String msg = "Removed unwanted word from FSN: " + fsnRoot + " became " + sanitizedFsnRoot;
			report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
		}
		return sanitizedFsnRoot;
	}
	

	public void determineConceptType(Concept concept) {
		//Simplest thing for Product Strength is that if there's a number in the FSN then it's probably Product Strength
		//We'll refine this logic as examples present themselves.
		String fsn = concept.getFsn();
		if (fsn.matches(".*\\d+.*")) {
			concept.setConceptType(ConceptType.PRODUCT_STRENGTH);
		} else {
			//If the concept has a dose form, then it's a Medicinal Form
			List<Relationship> doseFormAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_DOSE_FORM, ActiveState.ACTIVE);
			if (!doseFormAttributes.isEmpty()) {
				concept.setConceptType(ConceptType.MEDICINAL_FORM);
			}
		}
		debug ("Determined " + concept + " to be " + concept.getConceptType());
	}

	List<Concept> getConceptsOfType(Collection<Concept> concepts, ConceptType[] conceptTypes) {
		List<Concept> matching = new ArrayList<Concept>();
		for (Concept thisConcept : concepts) {
			for (ConceptType thisConceptType : conceptTypes) {
				if (thisConcept.getConceptType().equals(thisConceptType)) {
					matching.add(thisConcept);
				}
			}
		}
		return matching;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = graph.getConcept(lineItems[1]);
		c.setConceptType(lineItems[0]);
		return c;
	}

}
