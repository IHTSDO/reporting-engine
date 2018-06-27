package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
//import org.ihtsdo.termserver.scripting.delta.CaseSignificanceFixAll;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * Combination of DRUGS-363 to remove "/1 each" from preferred terms
 * DRUGS-461
 * DRUGS-486 - MP PTs must end in "product"
 * DRUGS-492 - CDs missing "precisely"
 * DRUGS-514 - Editorial Guide updated for MPFS eg "-containing"
 */
public class NormalizeDrugTerms extends DrugBatchFix implements RF2Constants{
	
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static Map<String, String> replacementMap = new HashMap<String, String>();
	private List<String> exceptions = new ArrayList<>();
	//CaseSignificanceFixAll csFixer;
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected NormalizeDrugTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		NormalizeDrugTerms fix = new NormalizeDrugTerms(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
		} finally {
			fix.finish();
		}
	}
	
	private void postInit() throws TermServerScriptException {
		//When we use the CS Fixer, we'll tell it to use our report printWriter rather than its own,
		//otherwise things get horribly confused!
		//This code modified to only report possible issues, not fix them.
		//csFixer = new CaseSignificanceFixAll(reportFile, printWriterMap, CaseSignificanceFixAll.Mode.REPORT_ONLY);
		
		/*	Concept doseFormRoot = gl.getConcept(421967003L);  // |Drug dose form (qualifier value)|);
		doseForms.add(" oral tablet");
		doseForms.add(" in oral dosage form");
		for (Concept doseForm : doseFormRoot.getDescendents(NOT_SET)) {
			Description pt = doseForm.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).get(0);
			doseForms.add(" " + pt.getTerm());
		}*/
	}

	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		/*exceptions.add("423967005");
		exceptions.add("319925005");
		exceptions.add("765078005");  //Not yet modelled 
		exceptions.add("765974009");  //Confusion tablet / capsule
		exceptions.add("765995004");  //Confusion tablet / capsule*/
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		SnomedUtils.populateConceptType(loadedConcept);
		/*if (loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			report (null, loadedConcept, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Skipping primitive MP");
			return 0;
		}*/
		//We'll take a little diversion here to correct the case significance of the ingredients
		//validateIngredientCaseSignficance(task, loadedConcept);
		int changesMade = termGenerator.ensureDrugTermsConform(task, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
		if (changesMade > 0) {
			saveConcept(task, loadedConcept, info);
		}
		return changesMade;
	}

	/*private void validateIngredientCaseSignficance(Task task, Concept c) throws TermServerScriptException {
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
			//Test making changes first, and then if it looks like fixes are needed, load substance and change for real
			Concept ingredient = gl.getConcept(r.getTarget().getConceptId());
			csFixer.normalizeCaseSignificance(ingredient, true);  
			/*if (changesMade > 0) {
				Concept loadedIngredient = loadConcept(ingredient, task.getBranchPath());
				changesMade = csFixer.normalizeCaseSignificance(loadedIngredient, true, false); //Not silently this time!
				if (changesMade > 0) {
					saveConcept(task, loadedIngredient, "");
				}
			}
		}
	}*/

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		debug("Identifying concepts to process");
		termGenerator.setQuiet(true);
		termGenerator.setPtOnly(false);
		
		List<Concept> allAffected = new ArrayList<Concept>(); 
		for (Concept c : gl.getConcept(subHierarchyStr).getDescendents(NOT_SET)) {
		/*	if (c.getConceptId().equals("714023005")) {
				debug("Checkpoint");
			} */
			SnomedUtils.populateConceptType(c);
			//Clone the concept so we're not modifying our local copy
			c = c.clone(c.getConceptId());
			if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
			//if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)) {
			//if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				if (exceptions.contains(c.getId())) {
					report (null, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
				} else {
					//See if the modifying the term makes any changes
					if (termGenerator.ensureDrugTermsConform(null, c, CharacteristicType.STATED_RELATIONSHIP) > 0) {
						allAffected.add(c);
					}
				}
			}
		}
		info ("Identified " + allAffected.size() + " concepts to process");
		termGenerator.setQuiet(false);
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
