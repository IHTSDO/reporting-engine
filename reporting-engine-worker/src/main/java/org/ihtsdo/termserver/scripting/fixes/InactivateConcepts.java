package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.StringUtils;

/*
 * INFRA-2496, QI-135
 * Inactivate concepts where a replacement exists - driven by list.
 * Children should have their Medical Procedure parent removed.
 */
public class InactivateConcepts extends BatchFix implements RF2Constants {
	
	Map<Concept, Concept> replacements = new HashMap<>();
	Map<Concept, Task> inactivations = new HashMap<>();
	boolean expectReplacements = false;
	
	protected InactivateConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		InactivateConcepts fix = new InactivateConcepts(null);
		try {
			fix.inputFileHasHeaderRow = false;
			fix.expectNullConcepts = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Just the FSNs
			fix.postInit(true);
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (loadedConcept == null) {
			report (task, concept, Severity.LOW, ReportActionType.NO_CHANGE, "Concept already deleted?");
		} else if (loadedConcept.isReleased()) {
			changesMade = inactivateConcept(task, loadedConcept);
			if (changesMade > 0) {
				save(task, loadedConcept, info);
			}
		} else {
			changesMade = deleteConcept(task, loadedConcept);
		}
		return changesMade;
	}
	
	private int inactivateConcept(Task task, Concept concept) throws TermServerScriptException {
		//Do we have a replacement for this concepts?  Only inactivate concepts with replacements
		Concept replacement = replacements.get(concept);
		if (replacement == null) {
			throw new ValidationFailure(concept, "Unable to inactivate without replacement");
		}
		
		//Have we already inactivated this concept?
		if (inactivations.containsKey(concept)) {
			report(task, concept, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Concept already inactivated in " + task.getKey());
			return NO_CHANGES_MADE;
		}
		
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		checkAndInactivatateIncomingAssociations(task, concept, InactivationIndicator.AMBIGUOUS, replacement);
		
		//Check for any stated children and remove this concept as a parent
		for (Concept child :  gl.getConcept(concept.getConceptId()).getDescendents(IMMEDIATE_CHILD, CharacteristicType.STATED_RELATIONSHIP)) {
			//Have we already inactivated this child
			if (!inactivations.containsKey(child)) {
				task.addAfter(child, concept);
				//Is this a concept we have to inactivate?  Go through whole process if so
				if (replacements.containsKey(child)) {
					report(task, child, Severity.LOW, ReportActionType.INFO, "Inactivating child of " + concept);
					doFix(task, child, " as descendant of " + concept);
				} else {
					//Otherwise, just remove the concept as a child of the parent
					removeParent(task, child, concept);
				}
			}
		}
		concept.setActive(false);
		concept.setEffectiveTime(null);
		if (replacement.equals(NULL_CONCEPT)) {
			concept.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			concept.setAssociationTargets(new AssociationTargets());
			report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as 'NonConformance to Editorial Policy'");
		} /*else {
			concept.setInactivationIndicator(InactivationIndicator.DUPLICATE);
			concept.setAssociationTargets(AssociationTargets.sameAs(replacement));
			report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as duplicate, same as: " + replacement);
		}*/
		else {
			concept.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
			concept.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
			report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as ambiguous, possibly equivalent to: " + replacement);
		}
		inactivations.put(concept, task);
		return CHANGE_MADE;
	}

	private void removeParent(Task task, Concept child, Concept parent) throws TermServerScriptException {
		
		child = loadConcept(child, task.getBranchPath());
		//Check we've got more than one stated parent!
		List<Relationship> parentRels = child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		if (parentRels.size() < 2) {
			throw new TermServerScriptException("Cannot inactivate " + parent + " it is the sole parent of " + child);
		}
		int changesMade = 0;
		for (Relationship parentRel : parentRels) {
			if (parentRel.getTarget().equals(parent)) {
				removeRelationship(task, child, parentRel);
				changesMade++;
			}
		}
		
		if (changesMade > 0) {
			save(task, child, "");
		} else {
			report (task, child, Severity.HIGH, ReportActionType.API_ERROR, "Did not remove " + parent + " as parent of " + child);
		}
	}
	
	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		checkAndInactivatateIncomingAssociations(t, c, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, null);
		report (t, c, Severity.MEDIUM, ReportActionType.CONCEPT_DELETED);
		return super.deleteConcept(t, c);
	}

	private void checkAndInactivatateIncomingAssociations(Task task, Concept c, InactivationIndicator reason, Concept replacement) throws TermServerScriptException {
		if (gl.usedAsHistoricalAssociationTarget(c) == null) {
			return;
		}
		for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
			inactivateHistoricalAssociation (task, assoc, reason, replacement);
		}
	}

	private void inactivateHistoricalAssociation(Task task, AssociationEntry assoc, InactivationIndicator reason, Concept replacement) throws TermServerScriptException {
		//The source concept can no longer have this historical association, and its
		//inactivation reason must also change.
		Concept incomingConcept = loadConcept(assoc.getReferencedComponentId(), task.getBranchPath());
		incomingConcept.setInactivationIndicator(reason);
		if (reason.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			incomingConcept.setAssociationTargets(null);
		} else if (reason.equals(InactivationIndicator.DUPLICATE)) {
			incomingConcept.setAssociationTargets(AssociationTargets.sameAs(replacement));
		} else if  (reason.equals(InactivationIndicator.AMBIGUOUS)) {
			incomingConcept.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
		} else {
			throw new IllegalArgumentException("Don't know what historical association to use with " + reason);
		}
		Severity severity = replacement == null ? Severity.CRITICAL : Severity.MEDIUM;
		report(task, incomingConcept, severity, ReportActionType.CONCEPT_CHANGE_MADE, "Historical association rewired to " + replacement);
		//Add this concept into our task so we know it's been updated
		task.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
		save(task, incomingConcept, "");
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c;
		
		if (StringUtils.isNumeric(lineItems[0])) {
			c = gl.getConcept(lineItems[0]);
		} else {
			c = gl.findConcept(lineItems[0]);
		}
		
		Concept replacement = null;
		if (lineItems.length > 1) {
			if (lineItems[1].trim().equals("N/A")) {
				replacement = NULL_CONCEPT;
			} else {
				try {
					replacement = gl.getConcept(lineItems[1]);
				} catch (Exception e) {
					if (expectReplacements) {
						warn ("Failed to identify replacement concept: " + lineItems[1]);
					}
				}
			}
		}
		
		if (replacement != null) {
			replacements.put(c, replacement);
			return Collections.singletonList(c);
		} else if (!expectReplacements){
			return Collections.singletonList(c);
		}
		return null;
	}
}
