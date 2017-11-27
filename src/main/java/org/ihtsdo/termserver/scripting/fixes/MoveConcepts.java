package org.ihtsdo.termserver.scripting.fixes;

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
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
For DRUG-413
Driven by a text file of concepts, move specified concepts to exist under
a parent concept (but keep relative position the same).  Reassign orphaned
children to their grandparents.
*/
public class MoveConcepts extends BatchFix implements RF2Constants{
	
	String parentNewLocation = "763087004"; //|TEMPORARY parent for concepts representing roles (product)
	//String parentNewLocation = "763019005"; // ** UAT **  |TEMPORARY parent for concepts representing roles (product)
	Relationship newParentRel;
	
	String childNewLocation = "373873005"; // |Pharmaceutical / biologic product (product)|
	Relationship newChildRel;
	
	protected MoveConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		MoveConcepts fix = new MoveConcepts(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
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
		Concept parentConcept =  gl.getConcept(parentNewLocation);
		parentConcept.setFsn("TEMPORARY parent for concepts representing roles (product)");
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
		newChildRel =  new Relationship(null, IS_A, gl.getConcept(childNewLocation), 0);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		List<Concept> modifiedConcepts = new ArrayList<Concept>();
		moveLocation(task, loadedConcept, modifiedConcepts);
		for (Concept thisModifiedConcept : modifiedConcepts) {
			try {
				String conceptSerialised = gson.toJson(thisModifiedConcept);
				debug ("Updating state of " + thisModifiedConcept + info);
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

	private void moveLocation(Task task, Concept loadedConcept, List<Concept> modifiedConcepts) throws TermServerScriptException {
		
		//Make sure we're working with a Primitive Concept
		/*if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined" );
		}*/
		loadedConcept.setConceptType(ConceptType.THERAPEUTIC_ROLE);
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		//List <Relationship> removedParentRels = new ArrayList<Relationship>();
		for (Relationship parentRel : parentRels) {
			remove (task, parentRel, loadedConcept, newParentRel.getTarget().toString(), true);
		}
		
		Relationship thisNewParentRel = newParentRel.clone(null);
		thisNewParentRel.setSource(loadedConcept);
		loadedConcept.addRelationship(thisNewParentRel);
		modifiedConcepts.add(loadedConcept);
		
		//Now we need to work through all the stated children and reassign them to the grandparents.
		List<Concept> statedChildren = gl.getConcept(loadedConcept.getConceptId()).getChildren(CharacteristicType.STATED_RELATIONSHIP);
		for (Concept child : statedChildren) {
			//replaceParentWithGrandparents(task, child, loadedConcept, removedParentRels, modifiedConcepts);
			replaceParents(task, child, loadedConcept, newChildRel, modifiedConcepts);
		}
	}

	/*private void replaceParentWithGrandparents(Task task, Concept child,
			Concept parent, 
			List<Relationship> grandParentRels,
			List<Concept> modifiedConcepts) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(child, task.getBranchPath());
		modifiedConcepts.add(loadedConcept);
		
		//Remove the current parent and add in all the grandparents
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																	IS_A,
																	ActiveState.ACTIVE));
		String replacementsAsList = toString(grandParentRels);
		for (Relationship parentRel : parentRels) {
			if (parentRel.getTarget().equals(parent)) {
				remove (task, parentRel, loadedConcept, replacementsAsList);
			}
		}
		
		//Now add all the grandparent relationships instead
		for (Relationship grandParent : grandParentRels) {
			Relationship parentRel = grandParent.clone(null);
			parentRel.setActive(true);
			parentRel.setSource(loadedConcept);
			loadedConcept.addRelationship(parentRel);
		}
	} */

	private void replaceParents(Task task, Concept child,
			Concept parent, Relationship newChildRel,
			List<Concept> modifiedConcepts) throws TermServerScriptException {
		
		//Check to see if this child is also going to be processed separately and skip if so.
		if (allConceptsToProcess.contains(child)) {
			report (task, child, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Child concept of " + parent + " already identified as a grouper.  Skipping..." );
			return;
		}
		
		String semTag = SnomedUtils.deconstructFSN(child.getFsn())[1];
		boolean makeFullyDefined = true;
		switch (semTag) {
			case "(medicinal product)" : child.setConceptType(ConceptType.MEDICINAL_PRODUCT);
										 break;
			case "(clinical drug)" : child.setConceptType(ConceptType.CLINICAL_DRUG);
										break;
			default : child.setConceptType(ConceptType.UNKNOWN);
						makeFullyDefined = false;
		}
		
		if (makeFullyDefined) {
			if (child.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
				child.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				report (task, child, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined" );
			}
		} else {
			report (task, child, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Unreconstructed concept, skipping attempt to make fully defined" );
		}
		
		Concept loadedConcept = loadConcept(child, task.getBranchPath());
		modifiedConcepts.add(loadedConcept);
		
		//Remove the current parents and add in the specified new location
		List<Relationship> parentRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																	IS_A,
																	ActiveState.ACTIVE);
		
		//Warn if we have more than one parent or no attributes
		if (parentRels.size() > 1) {
			report (task, child, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Child concept has more than one parent. Removing all." );
		}
		
		List<Relationship> allRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		if (allRels.size() - parentRels.size() == 0) {
			report (task, child, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Child concept has no attributes!" );
		}
		
		boolean replacementRequired = true;
		for (Relationship parentRel : parentRels) {
			if (!parentRel.getTarget().getConceptId().equals(childNewLocation)) {
				remove (task, parentRel, loadedConcept, newChildRel.getTarget().toString(), false);
			} else {
				replacementRequired = false;
				report (task, child, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Child concept already has target parent, no new parent to be added." );
			}
		}
		
		//If the target exists but inactive, then reactivate that relationship
		if (replacementRequired) {
			List<Relationship> inactiveRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.INACTIVE);
			for (Relationship thisInactiveRel : inactiveRels) {
				if (thisInactiveRel.getTarget().getConceptId().equals(childNewLocation)) {
					thisInactiveRel.setActive(true);
					report (task, child, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Child concept already has target parent as inactive relationship.  Reactivated." );
					replacementRequired = false;
				}
			}
		}
		
		//Now add in the replacement relationship if needed ie not already present
		if (replacementRequired) {
			Relationship parentRel = newChildRel.clone(null);
			parentRel.setActive(true);
			parentRel.setSource(loadedConcept);
			loadedConcept.addRelationship(parentRel);
		}
	}

	/*private String toString(List<Relationship> relationships) {
		StringBuffer buff = new StringBuffer();
		boolean isFirst = true;
		for (Relationship r : relationships) {
			if (!isFirst) {
				buff.append(" + ");
			} else {
				isFirst = false;
			}
			buff.append(r.getTarget());
		}
		return buff.toString();
	}*/

	private void remove(Task t, Relationship rel, Concept loadedConcept, String retained, boolean isParent) throws TermServerScriptException {
		String msgQualifier = isParent ? "parent's" : "child's";
		
		//Get our local copy of the concept since it knows the ConceptType
		Concept concept = gl.getConcept(loadedConcept.getConceptId());
		
		//Are we inactivating or deleting this relationship?
		if (rel.getEffectiveTime() == null || rel.getEffectiveTime().isEmpty()) {
			loadedConcept.removeRelationship(rel);
			report (t, concept, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, "Deleted " + msgQualifier + " parent relationship: " + rel.getTarget() + " in favour of " + retained);
		} else {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			report (t, concept, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, "Inactivated " + msgQualifier + " parent relationship: " + rel.getTarget() + " in favour of " + retained);
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}

}
