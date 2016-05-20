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
		fix.loadProject();
		//Recover the current project state from TS to allow quick searching of all concepts
		System.exit(0);
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
		//Remove the first row
		concepts.remove(0);
		debug ("Loading " + concepts.size() + " concepts from TermServer.");
		for (Concept thisConcept : concepts) {
			Concept loadedConcept = loadConcept(thisConcept, branchPath);
			//Work out a unique key by the concatenation of all inferred active ingredients
			List<Relationship> ingredients = loadedConcept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED);
			String comboKey = getIngredientCombinationKey(loadedConcept, ingredients);
			debug ("Loaded " + loadedConcept + " with " + comboKey.split(SEPARATOR).length + " active ingredients.");
			ingredientCombos.put(comboKey, loadedConcept);
		}
		println ("Formed " + concepts.size() + " concepts into " + ingredientCombos.keySet().size() + " batches.");
		//Now each of those concepts that shares that combination of ingredients can go into the same batch
		for (String thisComboKey : ingredientCombos.keySet()) {
			Collection<Concept> conceptsWithCombo = ingredientCombos.get(thisComboKey);
			Batch batchThisCombo = new Batch();
			batchThisCombo.setDescription(fileName + ": " + thisComboKey);
			batchThisCombo.setConcepts(new ArrayList<Concept>(conceptsWithCombo));
			debug ("Batched " + conceptsWithCombo.size() + " concepts with ingredients " + thisComboKey);
			batches.add(batchThisCombo);
		}
		return batches;
	}
	
	private void loadProject() throws SnowOwlClientException, TermServerFixException {
		File snapShotArchive = tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT);
		GraphLoader gl = GraphLoader.getGraphLoader();
		
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(snapShotArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						if (fileName.contains("rf2_Relationship_Snapshot")) {
							gl.loadRelationshipFile(zis);
						} else if (fileName.contains("rf2_Description_Snapshot")) {
							gl.loadDescriptionFile(zis);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
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
