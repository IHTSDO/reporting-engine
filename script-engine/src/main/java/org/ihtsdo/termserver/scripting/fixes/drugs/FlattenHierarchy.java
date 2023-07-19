package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
Updated for SUBST-254
For SUBST-215 From a given (now filtered) list of concepts, check that a current parent matches the 
expected target. Replace that parent relationship with "Is Modification Of" and
make the original grandparent(s) the new parent

Modifications added that siblings of concepts (via Modification Of parent) should be automatically included
And batch siblings together.

Also, if the base has any disposition, this will be copied into the modified concepts

Input file structure:  SourceId	SourceTerm	AttributeName	TargetId	TargetTerm
UPDATE: CONCEPT	FSN	BASE
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlattenHierarchy extends BatchFix implements ScriptConstants{

	private static Logger LOGGER = LoggerFactory.getLogger(FlattenHierarchy.class);

	Map<String,String> expectedTargetMap = new HashMap<>();
	Concept isModificationOf;
	Concept hasDisposition;
	Set<Concept> allRemodeledConcepts = new HashSet<>();
	List<Concept> finalCompleteSetConceptsToProcess;
	
	protected FlattenHierarchy(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
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
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		isModificationOf = gl.getConcept("738774007"); // |Is modification of (attribute)|
		hasDisposition = gl.getConcept("726542003");   // |Has disposition (attribute)|
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		if (!loadedConcept.isActive()) {
			report (t, loadedConcept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept is recently inactive - skipping");
			return 0;
		}
		loadedConcept.setConceptType(ConceptType.SUBSTANCE);
		List<Concept> modifiedConcepts = new ArrayList<Concept>();
		//At top level, we'll recover the expected target from the file, and we'll calculate the grandparents to be used.
		flattenHierarchy(t, loadedConcept, null, modifiedConcepts, null, null);
		for (Concept thisModifiedConcept : modifiedConcepts) {
			updateConcept(t, thisModifiedConcept, info);
		}
		incrementSummaryInformation("Concepts Modified", modifiedConcepts.size());
		incrementSummaryInformation(t.getKey(), modifiedConcepts.size());
		return modifiedConcepts.size();
	}

	private void flattenHierarchy(Task task, Concept loadedConcept, Concept expectedTarget, List<Concept> modifiedConcepts, Set<Relationship> potentialGrandParentRels, List<Concept> dispositions) throws TermServerScriptException {
		
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(SnomedUtils.countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP));
		
		if (expectedTarget == null) {
			String expectedTargetStr = expectedTargetMap.get(loadedConcept.getConceptId());
			expectedTarget = gl.getConcept(expectedTargetStr);
		}
		
		//Does our target have a disposition we need to copy to all modified descendants?
		if (dispositions == null) {
			Set<Relationship> dispositionRels = expectedTarget.getRelationships(CharacteristicType.STATED_RELATIONSHIP, hasDisposition, ActiveState.ACTIVE);
			dispositions = new ArrayList<>();
			for (Relationship r : dispositionRels) {
				dispositions.add(r.getTarget());
			}
		}
		
		//If we have more than one parent, or the parent is not as expected, then warn
		if (parentRels.size() == 0) {
			String msg = "No parents detected - concept inactive?";
			report (task, loadedConcept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg);
			return;
		} else if (parentRels.size() > 1) {
			String msg = parentRels.size() + " parents encountered";
			report (task, loadedConcept, Severity.LOW, ReportActionType.VALIDATION_CHECK, msg);
		} else {
			Concept actualTarget = parentRels.iterator().next().getTarget();
			if (!actualTarget.equals(expectedTarget)) {
				String msg = "Expected target " + expectedTarget + " did not match actual: " + actualTarget;
				report (task, loadedConcept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg);
			}
		}
		
		//If this is the top level concept, work out which grandparents via the target we're going 
		//to use as new parents (ie not redundant)
		if (potentialGrandParentRels == null) {
			//We'll take the grand parents (via expectedTarget) as the new parents...unless they're redundant due to the ancestors of any other parents.
			potentialGrandParentRels = new HashSet<Relationship> (expectedTarget.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																				IS_A,
																				ActiveState.ACTIVE));
		}
		
		Set<Relationship> ancestorRels = determineNonRedundantAncestors(task, loadedConcept, expectedTarget, parentRels, potentialGrandParentRels);
		
		String grandParentsDesc;
		if (ancestorRels.size() > 1) {
			String msg = "Multiple grandparents reassigned as parents: " + ancestorRels.size();
			report (task, loadedConcept, Severity.LOW, ReportActionType.INFO, msg);
			grandParentsDesc = ancestorRels.size() + " grandparents";
		} else if (ancestorRels.size() == 0) {
			String msg = "All grandparents already represented through existing parents";
			report (task, loadedConcept, Severity.HIGH, ReportActionType.INFO, msg);
			grandParentsDesc = "N/A";
		} else {
			grandParentsDesc = ancestorRels.iterator().next().getTarget().toString();
		}
		
		for (Relationship parentRel : parentRels) {
			//Only removing the parent that we're replacing as a modification
			if (parentRel.getTarget().equals(expectedTarget)) {
				removeParentRelationship (task, parentRel, loadedConcept, grandParentsDesc, null);
			}
		}
		
		for (Relationship ancestorRel : ancestorRels) {
			Concept ancestor = ancestorRel.getTarget();
			String msg = "Ancestor now parent: " + ancestor.toString();
			report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
			Relationship newParentRel = new Relationship(loadedConcept, IS_A, ancestor, 0);
			addOrReactivateRelationship(task, loadedConcept, newParentRel);
		}
		modifiedConcepts.add(loadedConcept);
		
		//Do we need to add one or more dispositions?
		if (dispositions != null && dispositions.size() > 0) {
			for (Concept disposition : dispositions) {
				Relationship dispRel = new Relationship (loadedConcept, hasDisposition, disposition, 0);
				if (!relationshipExists(loadedConcept, dispRel)) {
					String msg = "Adding disposition: " + disposition;
					addOrReactivateRelationship(task, loadedConcept, dispRel);
					report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
				}
			}
		}
		
		//Also add the original parent as a "modification of"
		Relationship modification = new Relationship(loadedConcept, isModificationOf, expectedTarget, 0);
		if (!relationshipExists(loadedConcept, modification)) {
			String msg = "Adding modification: " + modification;
			addOrReactivateRelationship(task, loadedConcept, modification);
			report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
		}
		//Now modify all children (recursively) using this concept as the new target
		//Work with the local concept, not the loaded one
		Concept localConcept = gl.getConcept(loadedConcept.getConceptId());
		for (Concept thisChild : localConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//If the concept has been specified specifically in the file, don't also process it as a descendant
			if (finalCompleteSetConceptsToProcess.contains(thisChild)) {
				String msg = "Ignoring attempt to process child " + thisChild + " of " + localConcept + " as specified originally in file.";
				report (task, thisChild, Severity.MEDIUM, ReportActionType.INFO, msg);
			} else {
				//Have we already modified this child via another concept?
				if (allRemodeledConcepts.contains(thisChild)) {
					report (task, thisChild, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept receiving multiple 'Is Modification Of' attributes");
				} else {
					allRemodeledConcepts.add(thisChild);
				}
				Concept thisChildLoaded = loadConcept(thisChild, task.getBranchPath());
				flattenHierarchy(task, thisChildLoaded, localConcept, modifiedConcepts, ancestorRels, dispositions);
			}
		}
	}

	private boolean relationshipExists(Concept c , Relationship r) {
		return c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).contains(r);
	}
	
	private void addOrReactivateRelationship(Task t, Concept c , Relationship newRel) throws TermServerScriptException {
		//If it already exists active, skip it
		if (relationshipExists(c, newRel)) {
			report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Relationship already exists active: " + newRel);
		} else {
			//Does it exists but inactive?
			for ( Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE)) {
				if (newRel.equals(r)) {
					r.setActive(true);
					report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REACTIVATED, "Reactivated: " + r);
					return;
				}
			}
		}
		//Otherwise, we're safe to add the relationship
		c.addRelationship(newRel);
	}

	private Set<Relationship> determineNonRedundantAncestors(Task task, Concept loadedConcept, Concept expectedTarget, Set<Relationship> parentRels, Set<Relationship> potentialGrandParentRels) throws TermServerScriptException {
		//Remove any grand parents that are already represented through other parents
		Set<Relationship> grandParentRels = new HashSet<>();
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
	
	@Override
	protected Batch formIntoBatch (List<Component> allConcepts) throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask(author_reviewer);
		
		//Include siblings of the specified concepts
		includeSiblings(allConcepts);
		//Take a copy of this list so we know what's been specified
		finalCompleteSetConceptsToProcess = asConcepts(allConcepts);
		
		List<Concept> unallocated = asConcepts(allConcepts);
		
		//Work through all concepts and find siblings (and descendants) to batch together
		//until we reach our batch size limit
		for (Component thisConcept : allConcepts) {
			//Have we already picked up this concept as a sibling or descendant?  Skip if so
			if (unallocated.contains(thisConcept)) {
				//Recursively work up and down and across hierarchy to group near concepts together
				allocateConceptToTask(task, thisConcept, unallocated);
				if (task.size() == 0) {
					LOGGER.warn ("Failed to allocate " + thisConcept + " to a task, adding anyway.");
					task.add(thisConcept);
					unallocated.remove(thisConcept);
				}
				LOGGER.debug (task + " (" + task.size() + ")");
				task = batch.addNewTask(author_reviewer);
			}
		}
		batch.consolidateIntoLargeTasks(taskSize, 0); //Tasks are large enough already, no wiggle room!
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_TO_PROCESS, allConcepts);
		for (Task t : batch.getTasks()) {
			LOGGER.debug (t + " (" + t.size() + ")");
		}
		return batch;
	}

	private void allocateConceptToTask(Task task, Component thisConcept, List<Concept> unallocated) throws TermServerScriptException {
		String sctId = ((Concept)thisConcept).getConceptId();
		String newTargetSctid = expectedTargetMap.get(sctId);
		Concept newTarget = gl.getConcept(newTargetSctid);
		
		//What siblings are still unallocated?  Clone list as we're modifying
		Set<Concept> siblings = new HashSet<>(newTarget.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		siblings.retainAll(unallocated);
		
		if (task.size() == 0  || task.size() + siblings.size() <= taskSize * wiggleRoom) {
			task.addAll(asComponents(siblings));
			unallocated.removeAll(siblings);
		} else {
			LOGGER.warn ("Unable to add " + siblings.size() + " siblings of " + thisConcept + " to task " + task);
		}
		
		//Work down the hierarchy from the siblings to try to include as many as possible
		Set<Concept> descendants = new HashSet<>();
		if (task.size() < taskSize) {
			for (Concept sibling : siblings) {
				//Are any of it's descendents unallocated?
				descendants = sibling.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);
				descendants.retainAll(unallocated);
				if (descendants.size() > 0) {
					if (task.size() + descendants.size() <= taskSize * wiggleRoom) {
					task.addAll(asComponents(descendants));
					unallocated.removeAll(descendants);
					} else {
						LOGGER.warn ("Unable to add " + descendants.size() + " descendants of " + sibling + " to task " + task);
					}
				}
			}
		}
		
		//Recursively check for siblings of the descendants, if we've space
		if (descendants.size() > 0 && task.size() < taskSize) {
			for (Concept descendant : descendants) {
				 allocateConceptToTask(task, descendant, unallocated);
			}
		}
		
		//Work up the hierarchy from the siblings to try to include as many as possible
		Set<Concept> ancestors = new HashSet<>();
		if (task.size() < taskSize) {
			for (Concept sibling : siblings) {
				//Are any of it's descendents unallocated?
				ancestors = sibling.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, false);
				ancestors.retainAll(unallocated);
				if (ancestors.size() > 0) {
					if (task.size() + ancestors.size() <= taskSize * wiggleRoom) {
						task.addAll(asComponents(ancestors));
						unallocated.removeAll(ancestors);
					} else {
						LOGGER.warn ("Unable to add " + ancestors.size() + " ancestors of " + sibling + " to task " + task);
					}
				}
			}
		}
		
		//Recursively check for siblings of the ancestors
		if (ancestors.size() > 0 && task.size() < taskSize) {
			for (Concept ancestor : ancestors) {
				 allocateConceptToTask(task, ancestor, unallocated);
			}
		}
	}

	private void includeSiblings(List<Component> allConcepts) throws TermServerScriptException {
		//For each concept, see if it has a sibling (via the base substance parent)
		//that has not been specifically specified.  Include it.
		List<Concept> previouslySpecified = asConcepts(allConcepts);
		for (Component c : previouslySpecified) {
			String newTargetSctid = expectedTargetMap.get(((Concept) c).getConceptId());
			Concept newTarget = gl.getConcept(newTargetSctid);
			//We need a copy of this list because we're going to remove from it.
			Set<Concept> siblings = new HashSet<>(newTarget.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
			//What siblings do we not already know about?
			siblings.removeAll(allConcepts);
			for (Concept sibling : siblings) {
				//Only include a sibling if the first word in the FSN matches that of the base
				if (sibling.getFsn().split(" ")[0].equals(newTarget.getFsn().split(" ")[0])) {
					allConcepts.add(sibling);
					expectedTargetMap.put(sibling.getConceptId(), newTargetSctid);
					LOGGER.warn ("Including " + sibling + ", the sibling of " + c  + " as a modification of " + newTarget);
				} else {
					LOGGER.warn ("Skipping " + sibling + ", the sibling of " + c  + " as a modification of " + newTarget + " due to name mismatch.");
				}
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (!c.isActive()) {
			report ((Task)null, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept is inactive - skipping");
			return null;
		}
		
		//Does this concept already have a modification attribute?
		String existing =  c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).stream()
				.map(rel -> rel.getTarget().toString())
				.collect (Collectors.joining(", "));
		if (existing != null && !existing.isEmpty()) {
			report ((Task)null, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept has existing modification(s): " + existing);
			return null;
		}
		Concept base = gl.getConcept(lineItems[2], false, true);
		expectedTargetMap.put(lineItems[0], base.getConceptId());
		return Collections.singletonList(c);
	}

}
