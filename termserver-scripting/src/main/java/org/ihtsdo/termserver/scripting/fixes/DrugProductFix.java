package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;

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
	
	private static final String SEPARATOR = "_";
	
	public static void main(String[] args) throws TermServerFixException, IOException, SnowOwlClientException {
		DrugProductFix fix = new DrugProductFix();
		fix.init(args);
		//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
		fix.loadProject();
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

		List<Batch> batches = new ArrayList<Batch>();
		debug ("Finding all concepts with ingredients...");
		Multimap<String, Concept> ingredientCombos = findAllIngredientCombos();
		
		//If the concept is of type Medicinal Entity, then put it in a batch with other concept with same ingredient combo
		for (Concept thisConcept : concepts) {
			if (thisConcept.getConceptType().equals(ConceptType.MEDICINAL_ENTITY)) {
				//thisConcept was loaded from file, we need the relationships from the concept loaded from the snapshot archive
				Concept fullConcept = GraphLoader.getGraphLoader().getConcept(thisConcept.getConceptId(), false);
				List<Relationship> ingredients = fullConcept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ACTIVE_STATE.ACTIVE);
				String comboKey = getIngredientCombinationKey(fullConcept, ingredients);
				//Get all concepts with this identical combination of ingredients
				Collection<Concept> matchingCombo = ingredientCombos.get(comboKey);
				Batch batchThisCombo = new Batch();
				batchThisCombo.setDescription(fileName + ": " + comboKey);
				batchThisCombo.setConcepts(new ArrayList<Concept>(matchingCombo));
				debug ("Batched " + fullConcept + " with " + comboKey.split(SEPARATOR).length + " active ingredients.  Batch size " + matchingCombo.size());
			}
		}
		return batches;
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
		for (Relationship r : ingredients) {
			if (r.isActive()) {
				comboKey += r.getTarget().getConceptId() + SEPARATOR;
			}
		}
		if (comboKey.isEmpty()) {
			//throw new TermServerFixException("Unable to find any ingredients for: " + loadedConcept);
			println ("*** Unable to find ingredients for " + loadedConcept);
			comboKey = "NONE";
		}
		return comboKey;
	}

	@Override
	public String getFixName() {
		return "MedicinalEntity";
	}
}
