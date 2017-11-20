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

import us.monoid.json.JSONObject;

/*
For DRUG-413
Driven by a text file of concepts, move specified concepts to exist under
a parent concept (but keep relative position the same).  Reassign orphaned
children to their grandparents.
*/
public class MoveConcepts extends BatchFix implements RF2Constants{
	
	//String target = "763087004"; //|TEMPORARY parent for concepts representing roles (product)
	String targetLocation = "373873005"; // |Pharmaceutical / biologic product (product)|
	Relationship newParentRel;  
	
	protected MoveConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		MoveConcepts fix = new MoveConcepts(null);
		try {
			fix.reportNoChange = true;
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
		newParentRel = new Relationship(null, IS_A, gl.getConcept(targetLocation), 0);
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
		return modifiedConcepts.size();
	}

	private void moveLocation(Task task, Concept loadedConcept, List<Concept> modifiedConcepts) throws TermServerScriptException {
		
		//Make sure we're working with a Primitive Concept
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined" );
		}
		
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		List <Relationship> removedParentRels = new ArrayList<Relationship>();
		for (Relationship parentRel : parentRels) {
			remove (task, parentRel, loadedConcept, newParentRel.getTarget().toString());
		}
		
		Relationship thisNewParentRel = newParentRel.clone(null);
		thisNewParentRel.setSource(loadedConcept);
		loadedConcept.addRelationship(thisNewParentRel);
		modifiedConcepts.add(loadedConcept);
		
		//Now we need to work through all the stated children and reassign them to the grandparents.
		List<Concept> statedChildren = gl.getConcept(loadedConcept.getConceptId()).getChildren(CharacteristicType.STATED_RELATIONSHIP);
		for (Concept child : statedChildren) {
			replaceParentWithGrandparents(task, child, loadedConcept, removedParentRels, modifiedConcepts);
		}
	}

	private void replaceParentWithGrandparents(Task task, Concept child,
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
	}

	private String toString(List<Relationship> relationships) {
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
	}

	private void remove(Task t, Relationship rel, Concept loadedConcept, String retained) {
		//Are we inactivating or deleting this relationship?
		if (rel.getEffectiveTime() == null || rel.getEffectiveTime().isEmpty()) {
			loadedConcept.removeRelationship(rel);
			report (t, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, "Deleted parent: " + rel.getTarget() + " in favour of " + retained);
		} else {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			report (t, loadedConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REMOVED, "Inactivated parent: " + rel.getTarget() + " in favour of " + retained);
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return new Concept(lineItems[0]);
	}

}
