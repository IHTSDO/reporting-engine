package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

import com.b2international.commons.StringUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

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
	
	protected DrugProductFix(BatchFix clone) {
		super(clone);
	}

	private static final String SEPARATOR = "_";
	
	ProductStrengthFix psf;
	MedicinalFormFix mff;
	MedicinalEntityFix mef;
	GrouperFix gf;
	
	public static void main(String[] args) throws TermServerFixException, IOException, SnowOwlClientException {
		DrugProductFix fix = new DrugProductFix(null);
		fix.init(args);
		//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
		fix.loadProject();
		fix.processFile();
	}
	
	protected void init(String[] args) throws TermServerFixException, IOException {
		super.init(args);
		psf = new ProductStrengthFix(this);
		mff = new MedicinalFormFix(this);
		mef = new MedicinalEntityFix(this);
		gf =  new GrouperFix(this);
	}

	@Override
	public int doFix(Task task, Concept concept) throws TermServerFixException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if (concept.getConceptType().equals(ConceptType.UNKNOWN)) {
			determineConceptType(concept);
		}
		loadedConcept.setConceptType(concept.getConceptType());
		int changesMade = 0;
		
		switch (concept.getConceptType()) {
			case MEDICINAL_ENTITY : changesMade = mef.doFix(task, loadedConcept);
									break;
			case MEDICINAL_FORM : changesMade = mff.doFix(task, loadedConcept);
									break;
			case PRODUCT_STRENGTH : changesMade = psf.doFix(task, loadedConcept);
									break;
			case GROUPER :			//No fixes being made to groupers for now
									//changesMade = gf.doFix(batch, loadedConcept);
									break;
			case PRODUCT_ROLE : 
			default : warn ("Don't know what to do with " + concept);
			report(task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept Type not determined.");
		}
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ("Updating state of " + loadedConcept);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				updateDescriptionInactivationReason(task, loadedConcept);
			}
		} catch (Exception e) {
			report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
		}
		return changesMade;
	}

	/**
	 * Any description that has been updated (effectiveTime == null) and is now inactive
	 * should have its inactivation reason set to RETIRED aka "Reason not stated"
	 * @param loadedConcept
	 * @throws  
	 */
	private void updateDescriptionInactivationReason(Task t, Concept loadedConcept) {
		for (Description d : loadedConcept.getDescriptions()) {
			if (d.getEffectiveTime() == null && d.isActive() == false) {
				try {
					String descriptionSerialised = gson.toJson(d);
					JSONObject jsonObjDesc = new JSONObject(descriptionSerialised);
					jsonObjDesc.put("id", d.getDescriptionId());
					jsonObjDesc.remove("descriptionId");
					jsonObjDesc.put("inactivationIndicator", InactivationIndicator.RETIRED.toString());
					jsonObjDesc.put("commitComment", "Batch Script Update");
					tsClient.updateDescription(jsonObjDesc, t.getBranchPath());
				} catch (SnowOwlClientException | JSONException e) {
					println ("Failed to set inactivation reason on " + d + ": " + e.getMessage());
				}
			}
		}
		
	}

	@Override
	List<Batch> formIntoBatches(String fileName, List<Concept> concepts, String branchPath) throws TermServerFixException {
		List<Batch> batches = new ArrayList<Batch>();
		debug ("Finding all concepts with ingredients...");
		Set<Concept> allConceptsToBeProcessed = new HashSet<Concept>();
		Multimap<String, Concept> ingredientCombos = findAllIngredientCombos();
		
		//If the concept is of type Medicinal Entity, then put it in a batch with other concept with same ingredient combo
		for (Concept thisConcept : concepts) {
			if (thisConcept.getConceptType().equals(ConceptType.MEDICINAL_ENTITY)) {
				List<Relationship> ingredients = getIngredients(thisConcept);
				String comboKey = getIngredientCombinationKey(thisConcept, ingredients);
				//Get all concepts with this identical combination of ingredients
				Collection<Concept> matchingCombo = ingredientCombos.get(comboKey);
				int taskCount = formIntoTasks(batches, matchingCombo, ingredients);
				allConceptsToBeProcessed.addAll(matchingCombo);
				debug ("Batched " + thisConcept + " with " + comboKey.split(SEPARATOR).length + " active ingredients.  Batch size " + matchingCombo.size() + " into " + taskCount + " tasks.");
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
		validateAllInputConceptsBatched (concepts, allConceptsToBeProcessed);
		return batches;
	}
	
	private int formIntoTasks(List<Batch> batches,
			Collection<Concept> matchingCombo, List<Relationship>ingredients) {
		Batch thisBatch = new Batch();
		batches.add(thisBatch);
		List<List<Concept>> taskContents = splitBatchIntoChunks(matchingCombo, batchSize);
		int taskCount = 0;
		for (List<Concept> thisTaskContents : taskContents) {
			Task thisTask = new Task (thisTaskContents);
			taskCount++;
			thisBatch.addTask(thisTask);
			String ingredientList = getIngredientList(ingredients);
			if (taskContents.size() > 1) {
				ingredientList += ": " + taskCount;
			}
			thisTask.setDescription(ingredientList);
		}
		return taskCount;
	}

	private void validateAllInputConceptsBatched(List<Concept> concepts,
			Set<Concept> allConceptsToBeProcessed) {
		//Ensure that all concepts we got given to process were captured in one batch or another
		for (Concept thisConcept : concepts) {
			if (!allConceptsToBeProcessed.contains(thisConcept) && !thisConcept.getConceptType().equals(ConceptType.GROUPER)) {
				String msg = thisConcept + " was given in input file but did not get included in a batch.  Check active ingredient.";
				report(null, thisConcept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.UNEXPECTED_CONDITION, msg);
			}
		}
		println("Processing " + allConceptsToBeProcessed.size() + " concepts.");
	}

	private List<Relationship> getIngredients(Concept c) {
		return c.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ACTIVE_STATE.ACTIVE);
	}
	
	private String getIngredientList(List<Relationship> ingredientRelationships) {
		ArrayList<String> ingredientNames = new ArrayList<String>();
		for (Relationship r : ingredientRelationships) {
			String ingredientName = r.getTarget().getFsn().replaceAll("\\(.*?\\)","").trim().toLowerCase();
			ingredientName = SnomedUtils.substitute(ingredientName, wordSubstitution);
			ingredientNames.add(ingredientName);
		}
		Collections.sort(ingredientNames);
		String list = ingredientNames.toString().replaceAll("\\[|\\]", "").replaceAll(", "," + ");
		return SnomedUtils.capitalize(list);
	}
	
	private Multimap<String, Concept> findAllIngredientCombos() throws TermServerFixException {
		Collection<Concept> allConcepts = GraphLoader.getGraphLoader().getAllConcepts();
		Multimap<String, Concept> ingredientCombos = ArrayListMultimap.create();
		for (Concept thisConcept : allConcepts) {
			List<Relationship> ingredients = thisConcept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ACTIVE_STATE.ACTIVE);
			if (ingredients.size() > 0) {
				String comboKey = getIngredientCombinationKey(thisConcept, ingredients);
				ingredientCombos.put(comboKey, thisConcept);
			}
		}
		return ingredientCombos;
	}

	private void loadProject() throws SnowOwlClientException, TermServerFixException {
		File snapShotArchive = new File (project + ".zip");
		//Do we already have a copy of the project locally?  If not, recover it.
		if (!snapShotArchive.exists()) {
			println ("Recovering current state of " + project + " from TS");
			tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, snapShotArchive);
		}
		GraphLoader gl = GraphLoader.getGraphLoader();
		println ("Loading archive contents into memory...");
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(snapShotArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						if (fileName.contains("sct2_Relationship_Snapshot")) {
							println("Loading Relationship File.");
							gl.loadRelationshipFile(zis);
						} else if (fileName.contains("sct2_Description_Snapshot")) {
							println("Loading Description File.");
							gl.loadDescriptionFile(zis);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				try{
					zis.closeEntry();
					zis.close();
				} catch (Exception e){} //Well, we tried.
			}
		} catch (IOException e) {
			throw new TermServerFixException("Failed to extract project state from archive " + snapShotArchive.getName(), e);
		}
	}

	private String getIngredientCombinationKey(Concept loadedConcept, List<Relationship> ingredients) throws TermServerFixException {
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
	public String getFixName() {
		return "MedicinalEntity";
	}
	

	protected int ensureAcceptableFSN(Task task, Concept concept, Map<String, String> wordSubstitution) throws TermServerFixException {
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
			report(task, concept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
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
			String newFSN, String semanticTag) throws TermServerFixException {
		String fullFSN = newFSN + SPACE + semanticTag;
		concept.setFsn(fullFSN);
		//FSNs are also preferred so we can just replace all preferred terms
		List<Description> fsnAndPreferred = concept.getDescriptions(ACCEPTABILITY.PREFERRED, null, ACTIVE_STATE.ACTIVE);
		for (Description thisDescription : fsnAndPreferred) {
			Description replacement = thisDescription.clone();
			thisDescription.setActive(false);
			thisDescription.setEffectiveTime(null);
			if (thisDescription.getType().equals(DESCRIPTION_TYPE.FSN)) {
				replacement.setTerm(fullFSN);
			} else {
				if (attemptAcceptableSYNPromotion(task, concept, newFSN, thisDescription)) {
					replacement = null;
				} else {
					replacement.setTerm(newFSN);
				}
				
				if (checkForDemotion(thisDescription, newFSN)) {
					String msg = "Demoted " + thisDescription + " to  " + SnomedUtils.toString(thisDescription.getAcceptabilityMap());
					report (task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
			
			if (replacement != null) {
				//Check to see if we're adding another description when we could better just increase
				//the acceptability of an existing preferred term
				String msg;
				Description improvedAcceptablity = attemptAcceptabilityImprovement(replacement, concept);
				if (improvedAcceptablity != null) {
					msg = "Improved acceptability of existing term: " + improvedAcceptablity + " now " + SnomedUtils.toString(improvedAcceptablity.getAcceptabilityMap());
				} else {
					concept.addDescription(replacement);
					msg = "Replaced (inactivated) " + thisDescription.getType() + " " + thisDescription + " with " + replacement;
				}
				report (task, concept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
				
			}
		}
		
	}

	private Description attemptAcceptabilityImprovement(
			Description replacement, Concept concept) throws TermServerFixException {
		//Look through all exising Preferred Terms to find if there's an existing one that matches
		//which could have it's acceptability improved instead of adding the replacement
		List<Description> preferredTerms = concept.getDescriptions(ACCEPTABILITY.PREFERRED, DESCRIPTION_TYPE.SYNONYM, ACTIVE_STATE.BOTH);
		Description improvedDescription = null;
		for (Description desc : preferredTerms) {
			if (desc.getTerm().equals(replacement.getTerm())) {
				int existingScore = SnomedUtils.accetabilityScore(desc.getAcceptabilityMap());
				Map<String, ACCEPTABILITY> mergedMap = SnomedUtils.mergeAcceptabilityMap(desc.getAcceptabilityMap(), replacement.getAcceptabilityMap());
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
		String origDescNorm = normalizeMultiIngredientTerm(originalDesc.getTerm());
		boolean isAcetaminophen = origDescNorm.toLowerCase().contains(ACETAMINOPHEN);
		if (!origDescNorm.equals(newFSN)) {
			//Demote the original description rather than inactivating it
			originalDesc.setActive(true);
			for (String dialect : originalDesc.getAcceptabilityMap().keySet()) {
				originalDesc.getAcceptabilityMap().put(dialect, ACCEPTABILITY.ACCEPTABLE);
			}
			if (isAcetaminophen) {
				originalDesc.getAcceptabilityMap().remove(GB_ENG_LANG_REFSET);
			}
			demotionPerformed = true;
		}
		return demotionPerformed;
	}

	private boolean attemptAcceptableSYNPromotion(Task task, Concept concept,
			String newTerm, Description oldDescription) throws TermServerFixException {
		//If we have a term which is only Acceptable (ie not preferred in either dialect)
		//then promote it to Preferred in the appropriate dialect
		boolean promotionSuccessful = false;
		List<Description> allAcceptable = concept.getDescriptions(ACCEPTABILITY.ACCEPTABLE, DESCRIPTION_TYPE.SYNONYM, ACTIVE_STATE.BOTH);
		List<Description> matchingAcceptable = new ArrayList<Description>();
		for (Description thisDesc : allAcceptable) {
			if (thisDesc.getTerm().equals(newTerm)) {
				matchingAcceptable.add(thisDesc);
			}
		}
		
		if (matchingAcceptable.size() > 1) {
			report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "More than one possible description promotion detected.");
		}
		
		if (matchingAcceptable.size() > 0) {
			Description promoting = matchingAcceptable.get(0);
			promoting.setActive(true);
			//Now find the dialects that were preferred in the old term and copy to new
			for (Map.Entry<String, ACCEPTABILITY> acceptablityEntry : oldDescription.getAcceptabilityMap().entrySet()) {
				String dialect = acceptablityEntry.getKey();
				ACCEPTABILITY a = acceptablityEntry.getValue();
				if (a.equals(ACCEPTABILITY.PREFERRED)) {
					promoting.getAcceptabilityMap().put(dialect, ACCEPTABILITY.PREFERRED);
					promotionSuccessful = true;
					String msg = "Promoted acceptable term " + promoting + " to be preferred in dialect " + dialect;
					report (task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
		}
		
		return promotionSuccessful;
	}

	protected String normalizeMultiIngredientTerm(String term) {
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

	private String removeUnwantedWords(Task task, Concept concept,
			String fsnRoot) {
		String modifiedFsnRoot = fsnRoot;
		for (String unwantedWord : unwantedWords) {
			String[] unwantedWordCombinations = new String[] { SPACE + unwantedWord, unwantedWord + SPACE };
			for (String thisUnwantedWord : unwantedWordCombinations) {
				if (modifiedFsnRoot.contains(thisUnwantedWord)) {
					modifiedFsnRoot.replace(thisUnwantedWord,"");
					String msg = "Removed unwanted word " + modifiedFsnRoot + " from FSN.";
					report(task, concept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
			
		}
		return modifiedFsnRoot;
	}
	

	void determineConceptType(Concept concept) {
		//Simplest thing for Product Strength is that if there's a number in the FSN then it's probably Product Strength
		//We'll refine this logic as examples present themselves.
		String fsn = concept.getFsn();
		if (fsn.matches(".*\\d+.*")) {
			concept.setConceptType(ConceptType.PRODUCT_STRENGTH);
		} else {
			//If the concept has a dose form, then it's a Medicinal Form
			List<Relationship> doseFormAttributes = concept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, HAS_DOSE_FORM, ACTIVE_STATE.ACTIVE);
			if (!doseFormAttributes.isEmpty()) {
				concept.setConceptType(ConceptType.MEDICINAL_FORM);
			}
		}
		debug ("Determined " + concept + " to be " + concept.getConceptType());
	}
	
	
	List<List<Concept>> splitBatchIntoChunks(Collection<Concept> concepts, int chunkSize) {
		List<Concept> batchContent = new ArrayList<Concept>(concepts);
		List<List<Concept>> chunkedList = new ArrayList<List<Concept>>();
		//How many chunks are we going to need?
		double chunkCount = Math.ceil((float)batchContent.size()/(float)chunkSize);
		//Now if we divide the concepts evenly between chunks how many in each?
		int conceptsPerChunk = (int)Math.round(batchContent.size() / chunkCount);
		
		//Any Medicinal Entity concepts must go in the first chunk
		List<Concept> thisChunk = new ArrayList<Concept>();
		chunkedList.add(thisChunk);
		List<Concept> MEs = getConceptsOfType(batchContent, ConceptType.MEDICINAL_ENTITY);
		thisChunk.addAll(MEs);
		batchContent.removeAll(MEs);
		while (batchContent.size() > 0) {
			Concept thisConcept = batchContent.get(0);
			batchContent.remove(thisConcept);
			thisChunk.add(thisConcept);
			if (thisChunk.size() == conceptsPerChunk && chunkedList.size() < chunkCount) {
				thisChunk = new ArrayList<Concept>();
				chunkedList.add(thisChunk);
			}
		}
		return chunkedList;
	}
	
	List<Concept> getConceptsOfType(Collection<Concept> concepts, ConceptType type) {
		List<Concept> matching = new ArrayList<Concept>();
		for (Concept thisConcept : concepts) {
			if (thisConcept.getConceptType().equals(type)) {
				matching.add(thisConcept);
			}
		}
		return matching;
	}

	/**
	For Medicinal entity and Medicinal form concepts only:
	If the FSN contains acetaminophen:
	Inactivate the FSN and create a new FSN changing acetaminophen to paracetamol and applying the usual rules eg: alpha order, spaces around +
	Concept should have one PT for US/GB corresponding to FSN (description may already exist or may need to be created)
	Change any PT containing acetaminophen to a synonym with US=A, GB=N and no changes eg: alpha order, spaces around +
	 */
	protected int processAcetaminophen(Batch batch, Concept concept) {
		// TODO Auto-generated method stub
		return 0;
	}


}
