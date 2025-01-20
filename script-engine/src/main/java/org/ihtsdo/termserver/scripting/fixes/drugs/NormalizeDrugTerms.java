package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.TermGenerator;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * Combination of DRUGS-363 to remove "/1 each" from preferred terms
 * DRUGS-461
 * DRUGS-486 - MP PTs must end in "product"
 * DRUGS-492 - CDs missing "precisely"
 * DRUGS-514 - Editorial Guide updated for MPFs eg "-containing"
 * DRUGS-560 - Editorial Guide updated for MPs eg "-containing"
 * DRUGS-562 - Editorial Guide updated for Structure and Disposition Groupers
 * DRUGS-786 - Batch terming update
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NormalizeDrugTerms extends DrugBatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(NormalizeDrugTerms.class);

	String subHierarchyStr = MEDICINAL_PRODUCT.getConceptId();
	private List<String> exceptions = new ArrayList<>();
	TermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected NormalizeDrugTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NormalizeDrugTerms fix = new NormalizeDrugTerms(null);
		try {
			ReportSheetManager.targetFolderId="1E6kDgFExNA9CRd25yZk_Y7l-KWRf8k6B"; //Drugs/Normalize Terming
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		//When we use the CS Fixer, we'll tell it to use our report printWriter rather than its own,
		//otherwise things get horribly confused!
		//This code modified to only report possible issues, not fix them.
		//csFixer = new CaseSignificanceFixAll(reportFile, printWriterMap, CaseSignificanceFixAll.Mode.REPORT_ONLY);
		
		super.postInit();
	}

	protected void init(String[] args) throws TermServerScriptException {
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

		//We'll take a little diversion here to correct the case significance of the ingredients
		//validateIngredientCaseSignficance(task, loadedConcept);
		
		int changesMade = termGenerator.ensureTermsConform(task, loadedConcept, CharacteristicType.INFERRED_RELATIONSHIP);
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
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
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept("446322005"));
	}
	*/
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.debug("Identifying concepts to process");
		termGenerator.setQuiet(true);
		
		List<Concept> allAffected = new ArrayList<Concept>(); 
		Set<Concept> selection = gl.getConcept(subHierarchyStr).getDescendants(NOT_SET);
		//Set<Concept> selection = Collections.singleton(gl.getConcept("785386009"));
		for (Concept c : selection) {
			SnomedUtils.populateConceptType(c);
			//Clone the concept so we're not modifying our local copy
			c = c.cloneWithIds();  //Exact copy - keep Ids
			if (isMP(c) || isMPF(c) || isCD(c)) {
			/*if (c.getConceptType().equals(ConceptType.STRUCTURAL_GROUPER) 
				|| c.getConceptType().equals(ConceptType.DISPOSITION_GROUPER)
				|| c.getConceptType().equals(ConceptType.STRUCTURE_AND_DISPOSITION_GROUPER)) { */
				if (exceptions.contains(c.getId())) {
					report((Task)null, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept manually listed as an exception");
				} else {
					//See if the modifying the term makes any changes
					//if (termGenerator.ensureDrugTermsConform(null, c, CharacteristicType.STATED_RELATIONSHIP) > 0) {
					if (termGenerator.ensureTermsConform(null, c, CharacteristicType.INFERRED_RELATIONSHIP) > 0) {
						allAffected.add(c);
					}
				}
			}
		}
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		termGenerator.setQuiet(false);
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}
/*
	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
	*/
	
	private boolean isMP(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}
	
	private boolean isMPF(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}
	
	private boolean isCD(Concept concept) {
		return concept.getConceptType().equals(ConceptType.CLINICAL_DRUG);
	}
}
