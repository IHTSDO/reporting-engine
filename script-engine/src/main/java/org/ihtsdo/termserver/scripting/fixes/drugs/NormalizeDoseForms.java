package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
DRUGS-514
Replace topical, ophthalmic and respiratory MPFs with cutaneous, ocular and pulmonary
Inactivate and replace concept
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NormalizeDoseForms extends DrugBatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(NormalizeDoseForms.class);

	Map<String, Concept> doseFormMap;
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected NormalizeDoseForms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NormalizeDoseForms fix = new NormalizeDoseForms(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT_COUNT, ATTRIBUTE_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		doseFormMap = new HashMap<>();
		doseFormMap.put("topical", gl.getConcept("740596000")); // |Cutaneous dose form (dose form)|
		doseFormMap.put("ophthalmic", gl.getConcept("385276004")); // |Ocular dose form (dose form)|
		doseFormMap.put("respiratory",gl.getConcept("764792001")); //  |Pulmonary dose form (dose form)|
	}
	
	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
				IS_A,
				ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(SnomedUtils.countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP));
		Relationship newParentRel = new Relationship (loadedConcept, IS_A, MEDICINAL_PRODUCT, UNGROUPED);
		
		int changes = replaceParents (task, loadedConcept, newParentRel, new String[] { parentCount, attributeCount });
		
		changes += replaceDoseForm(task, loadedConcept);
		
		changes += termGenerator.ensureTermsConform(task, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
		
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			
			int activeIngredientCount = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, Concept.HAS_ACTIVE_INGRED, ActiveState.ACTIVE).size();
			int doseFormCount = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, Concept.HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE).size();
			
			if (activeIngredientCount > 0 && doseFormCount > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changes++;
				report(task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - insufficient attributes!");
			}
		}
		
		try {
			cloneAndReplace(loadedConcept, task, InactivationIndicator.OUTDATED);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changes;
	}

	private int replaceDoseForm(Task t, Concept c) throws TermServerScriptException {
		//Not sure if this is even needed yet?
		Set<Relationship> doseForms = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE);
		if (doseForms.size() > 1) {
			report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, doseForms.size() + " dose forms detected");
		}
		for (String currentTerm : doseFormMap.keySet()) {
			if (c.getFsn().contains(currentTerm)) {
				replaceRelationship(t, c, HAS_MANUFACTURED_DOSE_FORM, doseFormMap.get(currentTerm), UNGROUPED, true);
				return CHANGE_MADE;
			}
		}
		return NO_CHANGES_MADE;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.debug("Identifying concepts to process");
		List<Component> processMe = new ArrayList<>();
		for (Concept c : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			SnomedUtils.populateConceptType(c);
			if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
				for (String currentTerm : doseFormMap.keySet()) {
					if (c.getFsn().contains(currentTerm)) {
						processMe.add(c);
						break;
					}
				}
			}
		}
		return processMe;
	}
}
