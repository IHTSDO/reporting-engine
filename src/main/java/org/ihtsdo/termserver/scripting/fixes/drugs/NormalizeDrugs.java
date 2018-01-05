package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
For DRUGS-422, DRUGS-434, DRUGS-435
Driven by a text file of concepts, move specified concepts to exist under
a new parent concept and remodel Terms.
*/
public class NormalizeDrugs extends DrugBatchFix implements RF2Constants{
	
	Relationship newParentRel;
	String newParent = "763158003"; // |Medicinal product (product)| 
	
	protected NormalizeDrugs(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		NormalizeDrugs fix = new NormalizeDrugs(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
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
		Concept parentConcept =  gl.getConcept(newParent);
		parentConcept.setFsn("Medicinal product (product)");
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changes = replaceParents (task, loadedConcept);
		changes += normalizeDrugTerms (task, loadedConcept);
		
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (countAttributes(loadedConcept) > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changes++;
				report (task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - no attributes!");
			}
		}
		
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changes;
	}

	private int replaceParents(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(countAttributes(loadedConcept));
		
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
		
		boolean replacementRequired = true;
		for (Relationship parentRel : parentRels) {
			if (!parentRel.equals(newParentRel)) {
				remove (task, parentRel, loadedConcept, newParentRel.getTarget().toString(), parentCount, attributeCount);
				changesMade++;
			} else {
				replacementRequired = false;
			}
		}
		
		if (replacementRequired) {
			Relationship thisNewParentRel = newParentRel.clone(null);
			thisNewParentRel.setSource(loadedConcept);
			loadedConcept.addRelationship(thisNewParentRel);
			changesMade++;
		}
		return changesMade;
	}

	private void remove(Task t, Relationship rel, Concept c, String retained, String parentCount, String attributeCount) throws TermServerScriptException {
		
		//Are we inactivating or deleting this relationship?
		String msg;
		ReportActionType action = ReportActionType.UNKNOWN;
		if (rel.getEffectiveTime() == null || rel.getEffectiveTime().isEmpty()) {
			c.removeRelationship(rel);
			msg = "Deleted parent relationship: " + rel.getTarget() + " in favour of " + retained;
			action = ReportActionType.RELATIONSHIP_DELETED;
		} else {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			msg = "Inactivated parent relationship: " + rel.getTarget() + " in favour of " + retained;
			action = ReportActionType.RELATIONSHIP_INACTIVATED;
		}
		
		report (t, c, Severity.LOW, action, msg, c.getDefinitionStatus().toString(), parentCount, attributeCount);
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
