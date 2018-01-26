package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.AssociationTargets;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
For DRUG-450
Driven by a text file of concepts, clone concepts - adjusting FSN and attributes
then inactivate original and add a historical association to the clone
*/
public class CloneAndReplace extends BatchFix implements RF2Constants{
	
	Set<Concept> allStatedTargets = new HashSet<>();
	Set<Concept> allInferredTargets = new HashSet<>();
	
	Map<Concept, Concept> newDoseForms = new HashMap<>();
	Concept hasDoseForm;
	
	protected CloneAndReplace(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CloneAndReplace fix = new CloneAndReplace(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT_COUNT, ATTRIBUTE_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.startTimer();
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
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
		if (!isSafeToInactivate(task, loadedConcept)) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept not safe to inactivate");
		} else {
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

	private boolean isSafeToInactivate(Task t, Concept c) {
		//If this concept has stated or inferred children OR if it's used as the target to some relationship, then 
		//it's not safe to inactivate
		String msg = "Concept is not safe to inactivate. ";

		if (c.getParents(CharacteristicType.STATED_RELATIONSHIP).size() > 0 ||
			c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
			msg += "It has descendants";
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			return false;
		}
		
		if (allStatedTargets.contains(c) || allInferredTargets.contains(c)) {
			msg += "It is used as the target of a relationship";
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
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
		clone.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		clone = updateConcept(task, clone, " cloned from " + loadedConcept);
		report (task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_ADDED, clone.toString());
		
		loadedConcept.setActive(false);
		loadedConcept.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
		loadedConcept.setAssociationTargets(AssociationTargets.mayBeA(clone));
		
		
	}

	private void remove(Task t, Relationship rel, Concept c, String retained, boolean isParent) throws TermServerScriptException {
		//Are we inactivating or deleting this relationship?
		if (rel.getEffectiveTime() == null || rel.getEffectiveTime().isEmpty()) {
			c.removeRelationship(rel);
			String msg = "Deleted parent relationship: " + rel.getTarget() + " in favour of " + retained;
			report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, msg);
		} else {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			String msg = "Inactivated parent relationship: " + rel.getTarget() + " in favour of " + retained;
			report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, msg);
		}
	}

	private Integer countAttributes(Concept c) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributeCount++;
			}
		}
		return attributeCount;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		return c;
	}

}
