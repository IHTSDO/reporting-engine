package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/*
For DRUGS-413, DRUGS-432, DRUGS-522
Driven by a text file of concepts, move specified concepts to exist under
a parent concept.  

Optionally Reassign orphaned children to their grandparents.

*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveConcepts extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(MoveConcepts.class);

	String parentNewLocation = "770654000"; // |TEMPORARY parent for CDs that are not updated (product)|
	//String parentNewLocation = "763087004"; //|TEMPORARY parent for concepts representing roles (product)
	//String parentNewLocation = "763019005"; // ** UAT **  |TEMPORARY parent for concepts representing roles (product)
	Relationship newParentRel;
	
	//String childNewLocation = "373873005"; // |Pharmaceutical / biologic product (product)|
	String childNewLocation = "763158003"; // |Medicinal product (product)| 
	Relationship newChildRel;
	boolean reassignOrphans = false;
	
	protected MoveConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		MoveConcepts fix = new MoveConcepts(null);
		try {
			fix.inputFileHasHeaderRow = true;
			fix.expectNullConcepts = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, ATTRIBUTES, STATED CHILDREN, INFERRED_CHILDREN";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		Concept parentConcept =  gl.getConcept(parentNewLocation);
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
		if (reassignOrphans) {
			newChildRel =  new Relationship(null, IS_A, gl.getConcept(childNewLocation), 0);
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		List<Concept> modifiedConcepts = new ArrayList<Concept>();
		moveLocation(t, loadedConcept, modifiedConcepts);
		for (Concept thisModifiedConcept : modifiedConcepts) {
			updateConcept(t, thisModifiedConcept, info);
		}
		incrementSummaryInformation(t.getKey(), modifiedConcepts.size());
		return modifiedConcepts.size();
	}

	private void moveLocation(Task t, Concept c, List<Concept> modifiedConcepts) throws TermServerScriptException {
		//Make sure we're working with a Primitive Concept
		/*if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined" );
		}*/
		//if we're not reassigning orphans, then no need to move a concept it it's parent is already being moved
		if (!reassignOrphans) {
			//Need to get ancestors on our locally loaded concept since it knows its full hierarchy
			Concept localConcept = gl.getConcept(c.getConceptId());
			Set<Concept> ancestors = localConcept.getAncestors(NOT_SET);
			if (!Collections.disjoint(allComponentsToProcess, ancestors)) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept has a parent already being moved. Skipping");
				//If we have multiple parents, it won't be a clean move!
				if (localConcept.getParents(CharacteristicType.STATED_RELATIONSHIP).size() > 1) {
					String parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP).stream().map(p -> p.toString()).collect(Collectors.joining(" + "));
					report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Skipped child concept not a clean move due to multiple stated parents: " + parents);
				}
				return;
			}
		}
		
		Set<Relationship> parentRels = new HashSet<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		if (parentRels.size() > 1) {
			//If we have multiple ingredients, this would make sense.  Change severity based on this.
			Severity severity = Severity.HIGH;
			String extraDetail = "";
			int ingredientCount = DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP).size();
			if (ingredientCount == parentRels.size()) {
				severity = Severity.MEDIUM;
			} else {
				extraDetail = ingredientCount + " ingredients but ";
			}
			String parents = parentRels.stream().map(r -> r.getTarget().getFsn()).collect(Collectors.joining(" + "));
			report(t, c, severity, ReportActionType.VALIDATION_CHECK, "Concept has " + extraDetail + parentRels.size() + " stated parents to remove: " + parents);
		}
		for (Relationship parentRel : parentRels) {
			remove (t, parentRel, c, newParentRel.getTarget().toString(), true);
		}
		
		Relationship thisNewParentRel = newParentRel.clone(null);
		thisNewParentRel.setSource(c);
		c.addRelationship(thisNewParentRel);
		modifiedConcepts.add(c);
		
		Severity severity = Severity.LOW;
		//Need the local concept to know about children
		Concept localConcept = gl.getConcept(c.getConceptId());
		int statedChildCount = localConcept.getChildren(CharacteristicType.STATED_RELATIONSHIP).size();
		int inferredChildCount = localConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size();
		if (statedChildCount > 0 || inferredChildCount > 0) {
			severity = Severity.HIGH;
		}
		report(t, c, severity, 
				ReportActionType.INFO, 
				"",
				c.getDefinitionStatus().toString(), 
				countAttributes(c, CharacteristicType.STATED_RELATIONSHIP),
				statedChildCount,
				inferredChildCount);
		
		if (reassignOrphans) {
			//Now we need to work through all the stated children and reassign them to the grandparents.
			Set<Concept> statedChildren = gl.getConcept(c.getConceptId()).getChildren(CharacteristicType.STATED_RELATIONSHIP);
			for (Concept child : statedChildren) {
				//replaceParentWithGrandparents(task, child, loadedConcept, removedParentRels, modifiedConcepts);
				replaceParents(t, child, c, newChildRel, modifiedConcepts);
			}
		}
	}

	/*private void replaceParentWithGrandparents(Task task, Concept child,
			Concept parent, 
			Set<Relationship> grandParentRels,
			List<Concept> modifiedConcepts) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(child, task.getBranchPath());
		modifiedConcepts.add(loadedConcept);
		
		//Remove the current parent and add in all the grandparents
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
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
		if (allComponentsToProcess.contains(child)) {
			report(task, child, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Child concept of " + parent + " already identified as a grouper.  Skipping..." );
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
				report(task, child, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined" );
			}
		} else {
			report(task, child, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Unreconstructed concept, skipping attempt to make fully defined" );
		}
		
		Concept loadedConcept = loadConcept(child, task.getBranchPath());
		modifiedConcepts.add(loadedConcept);
		
		//Remove the current parents and add in the specified new location
		Set<Relationship> parentRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																	IS_A,
																	ActiveState.ACTIVE);
		
		//Warn if we have more than one parent or no attributes
		if (parentRels.size() > 1) {
			report(task, child, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Child concept has more than one parent. Removing all." );
		}
		
		Set<Relationship> allRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		if (allRels.size() - parentRels.size() == 0) {
			report(task, child, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Child concept has no attributes!" );
		}
		
		boolean replacementRequired = true;
		for (Relationship parentRel : parentRels) {
			if (!parentRel.getTarget().getConceptId().equals(childNewLocation)) {
				remove (task, parentRel, loadedConcept, newChildRel.getTarget().toString(), false);
			} else {
				replacementRequired = false;
				report(task, child, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Child concept already has target parent, no new parent to be added." );
			}
		}
		
		//If the target exists but inactive, then reactivate that relationship
		if (replacementRequired) {
			Set<Relationship> inactiveRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.INACTIVE);
			for (Relationship thisInactiveRel : inactiveRels) {
				if (thisInactiveRel.getTarget().getConceptId().equals(childNewLocation)) {
					thisInactiveRel.setActive(true);
					report(task, child, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Child concept already has target parent as inactive relationship.  Reactivated." );
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

	/*private String toString(Set<Relationship> relationships) {
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

	private void remove(Task t, Relationship rel, Concept c, String retained, boolean isParent) throws TermServerScriptException {
		String msgQualifier = isParent ? "parent's" : "child's parent";
		Integer attributeCount = countAttributes(c, CharacteristicType.STATED_RELATIONSHIP);
		
		//Are we inactivating or deleting this relationship?
		if (rel.getEffectiveTime() == null || rel.getEffectiveTime().isEmpty()) {
			c.removeRelationship(rel);
			String msg = "Deleted " + msgQualifier + " relationship: " + rel.getTarget() + " in favour of " + retained;
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, msg, c.getDefinitionStatus().toString(), attributeCount.toString());
		} else {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			String msg = "Inactivated " + msgQualifier + " relationship: " + rel.getTarget() + " in favour of " + retained;
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, msg, c.getDefinitionStatus().toString(), attributeCount.toString());
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (!c.isActive()) {
			LOGGER.warn (c + " is not active, skipping");
			return null;
		}
		//c.setConceptType(ConceptType.THERAPEUTIC_ROLE);
		SnomedUtils.populateConceptType(c);
		return Collections.singletonList(c);
	}

}
