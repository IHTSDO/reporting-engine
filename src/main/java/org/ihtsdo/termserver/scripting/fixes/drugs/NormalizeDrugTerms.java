package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.delta.CaseSignificanceFixAll;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * Combination of DRUGS-363 to remove "/1 each" from preferred terms
 * DRUGS-461
 */
public class NormalizeDrugTerms extends DrugBatchFix implements RF2Constants{
	
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static Map<String, String> replacementMap = new HashMap<String, String>();
	private List<String> exceptions = new ArrayList<>();
	CaseSignificanceFixAll csFixer;
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
			fix.startTimer();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			info ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	private void postInit() throws TermServerScriptException {
		//When we use the CS Fixer, we'll tell it to use our report printWriter rather than its own,
		//otherwise things get horribly confused!
		//This code modified to only report possible issues, not fix them.
		csFixer = new CaseSignificanceFixAll(reportFile, printWriterMap, CaseSignificanceFixAll.Mode.REPORT_ONLY);
		Concept doseFormRoot = gl.getConcept(421967003L);  // |Drug dose form (qualifier value)|);
		doseForms.add(" oral tablet");
		doseForms.add(" in oral dosage form");
		for (Concept doseForm : doseFormRoot.getDescendents(NOT_SET)) {
			Description pt = doseForm.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).get(0);
			doseForms.add(" " + pt.getTerm());
		}
	}

	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		exceptions.add("423967005");
		exceptions.add("319925005");
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		SnomedUtils.populateConceptType(loadedConcept);
		if (loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			report (null, loadedConcept, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Skipping primitive MP");
			return 0;
		}
		//We'll take a little diversion here to correct the case significance of the ingredients
		validateIngredientCaseSignficance(task, loadedConcept);
		int changesMade = termGenerator.ensureDrugTermsConform(task, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
		if (changesMade > 0) {
			saveConcept(task, loadedConcept, info);
		}
		return changesMade;
	}

	private void validateIngredientCaseSignficance(Task task, Concept c) throws TermServerScriptException {
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
			}*/
		}
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		debug("Identifying concepts to process");
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : gl.getConcept(subHierarchyStr).getDescendents(NOT_SET)) {
			if (exceptions.contains(c.getId())) {
				report (null, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
			} else {
				SnomedUtils.populateConceptType(c);
				if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)) {
					allAffected.add(c);
				}
				
				//Now check for multi ingredients out of order in any term
				/*for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getTerm().contains(AND)) {
						String normalized = normalizeMultiIngredientTerm(d.getTerm(), d.getType(), c.getConceptType());
						if (!normalized.equals(d.getTerm())) {
							allAffected.add(c);
							break;
						}
					}
				}*/
			}
		}
		info ("Identified " + allAffected.size() + " concepts to process");
		return new ArrayList<Component>(allAffected);
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
