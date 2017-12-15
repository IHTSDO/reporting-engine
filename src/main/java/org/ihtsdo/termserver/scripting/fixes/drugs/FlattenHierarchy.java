package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/*
For SUBST-215 From a given list of concepts, check that a current parent matches the 
expected target. Replace that parent relationship with "Is Modification Of" and
make the original grandparent(s) the new parent

Input file structure:  SourceId	SourceTerm	AttributeName	TargetId	TargetTerm
*/
public class FlattenHierarchy extends BatchFix implements RF2Constants{
	
	Map<String,String> expectedTargetMap = new HashMap<>();
	Concept isModificationOf;
	
	protected FlattenHierarchy(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		FlattenHierarchy fix = new FlattenHierarchy(null);
		try {
			fix.inputFileHasHeaderRow = true;
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
		isModificationOf = gl.getConcept("738774007"); // |Is modification of (attribute)|
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		List<Concept> modifiedConcepts = new ArrayList<Concept>();
		flattenHierarchy(task, loadedConcept, modifiedConcepts);
		for (Concept thisModifiedConcept : modifiedConcepts) {
			try {
				String conceptSerialised = gson.toJson(thisModifiedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + thisModifiedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		incrementSummaryInformation(task.getKey(), modifiedConcepts.size());
		return modifiedConcepts.size();
	}

	private void flattenHierarchy(Task task, Concept loadedConcept, List<Concept> modifiedConcepts) throws TermServerScriptException {
		
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(countAttributes(loadedConcept));
		String expectedTargetStr = expectedTargetMap.get(loadedConcept.getConceptId());
		Concept expectedTarget = gl.getConcept(expectedTargetStr);
		
		//If we have more than one parent, or the parent is not as expected, then warn
		if (parentRels.size() == 0) {
			String msg = "No parents detected - concept inactive?";
			report (task, loadedConcept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg);
			return;
		} else if (parentRels.size() > 1) {
			String msg = parentRels.size() + " parents encountered";
			report (task, loadedConcept, Severity.LOW, ReportActionType.VALIDATION_CHECK, msg);
		} else {
			String actualTarget = parentRels.get(0).getTarget().getConceptId();
			if (!actualTarget.equals(expectedTargetStr)) {
				String msg = "Expected target " + expectedTargetStr + " did not match actual: " + actualTarget;
				report (task, loadedConcept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg);
			}
		}
		
		//Work out which grandparents via the target we're going to use as new parents (ie not redundant)
		List<Relationship> grandParentRels = determineGrandParents(task, loadedConcept, expectedTarget, parentRels);
		
		String grandParentsDesc;
		if (grandParentRels.size() > 1) {
			String msg = "Multiple grandparents reassigned as parents: " + grandParentRels.size();
			report (task, loadedConcept, Severity.LOW, ReportActionType.INFO, msg);
			grandParentsDesc = grandParentRels.size() + " grandparents";
		} else if (grandParentRels.size() == 0) {
			String msg = "All grandparents already represented through existing parents";
			report (task, loadedConcept, Severity.HIGH, ReportActionType.INFO, msg);
			grandParentsDesc = "N/A";
		} else {
			grandParentsDesc = grandParentRels.get(0).getTarget().toString();
		}
		
		for (Relationship parentRel : parentRels) {
			//Only removing the parent that we're replacing as a modification
			if (parentRel.getTarget().equals(expectedTarget)) {
				remove (task, parentRel, loadedConcept, grandParentsDesc, true);
			}
		}
		
		for (Relationship grandParentRel : grandParentRels) {
			Concept grandParent = grandParentRel.getTarget();
			String msg = "Grandparent now parent: " + grandParent.toString();
			report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
			Relationship newParentRel = new Relationship(loadedConcept, IS_A, grandParent, 0);
			loadedConcept.addRelationship(newParentRel);
		}
		modifiedConcepts.add(loadedConcept);
		
		//Also add the original parent as a "modification of"
		Relationship modification = new Relationship(loadedConcept, isModificationOf, expectedTarget, 0);
		String msg = "Adding modification: " + modification;
		loadedConcept.addRelationship(modification);
		report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
	}

	private List<Relationship> determineGrandParents(Task task, Concept loadedConcept, Concept expectedTarget, List<Relationship> parentRels) throws TermServerScriptException {
		//We'll take the grand parents (via expectedTarget) as the new parents...unless they're redundant due to the ancestors of any other parents.
		List<Relationship> potentialGrandParentRels = new ArrayList<Relationship> (expectedTarget.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																			IS_A,
																			ActiveState.ACTIVE));
		
		//Remove any grand parents that are already represented through other parents
		List<Relationship> grandParentRels = new ArrayList<>();
		for (Relationship potentialGrandParentRel : potentialGrandParentRels) {
			boolean isAlreadyRepresented = false;
			for (Relationship parentRel : parentRels) {
				if (!parentRel.getTarget().equals(expectedTarget)) {
					//does this parents ancestors include our potential grandParent?  No need to include that grandparent if so
					//Need to switch back to our pre-loaded concept to get this information
					Concept preLoadedParent = gl.getConcept(parentRel.getTarget().getConceptId());
					Set<Concept> ancestors = preLoadedParent.getAncestors(NOT_SET);
					if (ancestors.contains(potentialGrandParentRel.getTarget())) {
						String msg = "Ignoring grandParent " + potentialGrandParentRel.getTarget() + " as already represented via " + parentRel.getTarget();
						report (task, loadedConcept, Severity.MEDIUM, ReportActionType.INFO, msg);
						isAlreadyRepresented = true;
						break;
					}
				}
			}
			if (!isAlreadyRepresented) {
				grandParentRels.add(potentialGrandParentRel);
			}
		}
		return grandParentRels;
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
		expectedTargetMap.put(lineItems[0], lineItems[3]);
		return c;
	}

}
