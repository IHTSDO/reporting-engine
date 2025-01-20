package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
For DRUGS-450, DRUGS-452, DRUGS-455, DRUGS-456, DRUGS-458
Driven by a text file of concepts, clone concepts - adjusting FSN and attributes
then inactivate original and add a historical association to the clone
Edit: Added column to specify inactivation reason on a per concept basis
*/
public class CloneAndReplace extends BatchFix implements ScriptConstants{

	Set<Concept> allStatedTargets = new HashSet<>();
	Set<Concept> allInferredTargets = new HashSet<>();
	
	Map<Concept, Concept> newDoseForms = new HashMap<>();
	Map<Concept, String> newFSNs = new HashMap<>();
	Map<Concept, InactivationIndicator> inactivationReasons = new HashMap<>();
	Map<Concept, String> historicalAssociations = new HashMap<>();
	Concept hasDoseForm;
	
	protected CloneAndReplace(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CloneAndReplace fix = new CloneAndReplace(null);
		try {
			//fix.runStandAlone = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT_COUNT, ATTRIBUTE_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postLoadInit();
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//Find all concepts that appear as attributes of another concept
		for (Concept c : gl.getAllConcepts()) {
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!r.getType().equals(IS_A)) {
					allInferredTargets.add(r.getTarget());
				}
			}
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!r.getType().equals(IS_A)) {
					allStatedTargets.add(r.getTarget());
				}
			}
		}
		
		hasDoseForm = gl.getConcept("411116001"); // |Has manufactured dose form (attribute)|
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		identifyType(loadedConcept);
		
		//Can we safely save this clone before we inactivate the original?
		if (!loadedConcept.isActive()) {
			report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept is already inactive");
		} else if (isSafeToInactivate(task, concept)) {  //Need to use local version since loadedConcept will not have parents populated
			changesMade = cloneAndReplace(task, loadedConcept);
			if (changesMade > 0) {
				try {
					updateConcept(task, loadedConcept, info);
				} catch (Exception e) {
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
				}
			}
		}
		return changesMade;
	}

	private boolean isSafeToInactivate(Task t, Concept c) throws TermServerScriptException {
		//If this concept has stated or inferred children OR if it's used as the target to some relationship, then 
		//it's not safe to inactivate
		String msg = "Concept is not safe to inactivate. ";

		if (c.getChildren(CharacteristicType.STATED_RELATIONSHIP).size() > 0 ||
			c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
			msg += "It has descendants";
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			return false;
		}
		
		if (allStatedTargets.contains(c) || allInferredTargets.contains(c)) {
			msg += "It is used as the target of a relationship";
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			return false;
		}
		
		return true;
	}

	private void identifyType(Concept loadedConcept) {
		String semTag = SnomedUtils.deconstructFSN(loadedConcept.getFsn())[1];
		switch (semTag) {
			case "(medicinal product form)" : loadedConcept.setConceptType(ConceptType.MEDICINAL_PRODUCT_FORM);
												break;
			case "(product)" : loadedConcept.setConceptType(ConceptType.PRODUCT);
								break;
			case "(medicinal product)" : loadedConcept.setConceptType(ConceptType.MEDICINAL_PRODUCT);
										 break;
			case "(clinical drug)" : loadedConcept.setConceptType(ConceptType.CLINICAL_DRUG);
										break;
			default : loadedConcept.setConceptType(ConceptType.UNKNOWN);
		}
	}

	private int cloneAndReplace(Task task, Concept loadedConcept) throws TermServerScriptException {
		Concept clone = loadedConcept.clone();
		remodel(task, clone, loadedConcept);
		
		//Save clone to TS
		clone = createConcept(task, clone, " cloned from " + loadedConcept);
		report(task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_ADDED, clone.toString());
		
		//If the save of the clone didn't throw an exception, we can inactivate the original
		inactivateConcept(task, loadedConcept, clone, inactivationReasons.get(loadedConcept));
		report(task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_INACTIVATED, "");
		
		return CHANGE_MADE;
	}

	/*
	 * Set the FSN and correct dose form on the cloned concept
	 */
	private void remodel(Task t, Concept clone, Concept original) throws TermServerScriptException {
		//The FSN is a new object, so no need to inactivate, just change directly
		String newFSN = newFSNs.get(original);
		Description fsn = clone.getFSNDescription();
		fsn.setTerm(newFSN);
		
		//Also the preferred term.  Remove all current preferred terms and replace
		//to avoid clashes with US / GB spellings
		List<Description> PTs = clone.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		for (Description thisPT : PTs) {
			clone.removeDescription(thisPT);
		}
		
		//Remove the semantic tag
		String pTerm = SnomedUtils.deconstructFSN(newFSN)[0];
		addSynonym(clone, pTerm, Acceptability.PREFERRED, ENGLISH_DIALECTS);
		
		//Now find the dose form and replace that - again directly.
		int doseFormsReplaced = 0;
		for (Relationship r : clone.getRelationships(CharacteristicType.STATED_RELATIONSHIP, hasDoseForm, ActiveState.ACTIVE)) {
			r.setTarget(newDoseForms.get(original));
			doseFormsReplaced++;
		}
		
		if (doseFormsReplaced == 0) {
			//If we didn't replace a relationship, we need to add a new one
			Relationship newDoseForm = new Relationship(clone, hasDoseForm, newDoseForms.get(original), 0);
			clone.addRelationship(newDoseForm);
			report(t, clone, Severity.HIGH, ReportActionType.RELATIONSHIP_ADDED, "Original had no stated dose form.  Added " + newDoseForm  );
		} else if (doseFormsReplaced > 1) {
			report(t, clone, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, doseFormsReplaced + " dose form attributes replaced!?" );
		}
	}

	/*	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		
		Concept c = gl.getConcept(lineItems[2]);
		inactivationReasons.put(c, InactivationIndicator.valueOf(lineItems[0].trim().toUpperCase()));
		historicalAssociations.put(c, lineItems[1]);
		
		//What's the new FSN?
		String newFSN = lineItems[4].trim();
		newFSNs.put(c, newFSN);
		
		//What's the correct dose Form
		String newDoseForm = lineItems[5];
		String newDoseFormSctid = newDoseForm.trim().replaceFirst("\\|", " ").split(" ")[0];
		Concept newDoseFormConcept = gl.getConcept(newDoseFormSctid);
		newDoseForms.put(c, newDoseFormConcept);
		return c;
	}*/
	
	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
			
			Concept c = gl.getConcept(lineItems[0]);
			//inactivationReasons.put(c, InactivationIndicator.valueOf(lineItems[0].trim().toUpperCase()));
			inactivationReasons.put(c, InactivationIndicator.AMBIGUOUS);
			//historicalAssociations.put(c, lineItems[1]);
			historicalAssociations.put(c, "POSSIBLY EQUIVALENT TO");
			
			//What's the new FSN?
			String newFSN = lineItems[2].trim();
			newFSNs.put(c, newFSN);
			
			//What's the correct dose Form
			String newDoseForm = lineItems[3];
			String newDoseFormSctid = newDoseForm.trim().replaceFirst("\\|", " ").split(" ")[0];
			Concept newDoseFormConcept = gl.getConcept(newDoseFormSctid);
			newDoseForms.put(c, newDoseFormConcept);
			return Collections.singletonList(c);
		}

}
