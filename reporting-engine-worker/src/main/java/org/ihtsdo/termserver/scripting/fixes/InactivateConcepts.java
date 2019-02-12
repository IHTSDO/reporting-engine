package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.StringUtils;

/*
 * INFRA-2496, QI-135, DRUGS-667, IHTSDO-175
 * Inactivate concepts where a replacement exists - driven by list.
 */
public class InactivateConcepts extends BatchFix implements RF2Constants {
	
	Map<Concept, Concept> replacements = new HashMap<>();
	Map<Concept, InactivationIndicator> inactivationIndicators = new HashMap<>();
	Map<Concept, Task> inactivations = new HashMap<>();
	boolean expectReplacements = false;
	boolean autoInactivateChildren = false;
	boolean rewireChildrenToGrandparents = true;
	
	protected InactivateConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		InactivateConcepts fix = new InactivateConcepts(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.inputFileHasHeaderRow = false;
			fix.expectNullConcepts = false;
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = 0;
		if (loadedConcept == null || !loadedConcept.isActive()) {
			report (t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Concept already inactivated?");
		} else if (loadedConcept.isReleased()) {
			changesMade = inactivateConcept(t, loadedConcept);
			if (changesMade > 0) {
				save(t, loadedConcept, info);
			}
		} else {
			changesMade = deleteConcept(t, loadedConcept);
		}
		return changesMade;
	}
	
	private int inactivateConcept(Task t, Concept c) throws TermServerScriptException {
		//Do we have a replacement for this concepts?  
		Concept replacement = replacements.get(c);
		if (expectReplacements && replacement == null) {
			throw new ValidationFailure(c, "Unable to inactivate without replacement");
		}
		
		List<Concept> parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		
		//Have we already inactivated this concept?
		if (inactivations.containsKey(c)) {
			report(t, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Concept already inactivated in " + t.getKey());
			return NO_CHANGES_MADE;
		}
		
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		//With the same inactivation reasons
		InactivationIndicator inactivationIndicator = inactivationIndicators.get(c);
		checkAndInactivatateIncomingAssociations(t, c, inactivationIndicator, replacement);
		
		//How many children do we have to do something different with?
		//Use locally held concept when traversing transative closure
		Set<Concept> descendants = gl.getConcept(c.getConceptId()).getDescendents(NOT_SET, CharacteristicType.STATED_RELATIONSHIP);
		descendants.removeAll(inactivations.keySet());
		if (descendants.size() > 0) {
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Inactivated concept has " + descendants.size() + " descendants not scheduled for inactivation");
		}
		
		//Check for any stated children and remove this concept as a parent
		for (Concept child : gl.getConcept(c.getConceptId()).getDescendents(IMMEDIATE_CHILD, CharacteristicType.STATED_RELATIONSHIP)) {
			//Have we already inactivated this child
			if (!inactivations.containsKey(child)) {
				t.addAfter(child, c);
				//Is this a concept we have to inactivate?  Go through whole process if so
				if (replacements.containsKey(child) || autoInactivateChildren) {
					String extraInfo = " as per file";
					Severity severity = Severity.LOW;
					if (!replacements.containsKey(child)) {
						//In this case, we should inactivate the child with the same details as the parent
						inactivationIndicators.put(child, inactivationIndicator);
						replacements.put(child, replacement);
						extraInfo = " NOT SPECIFIED IN FILE";
						severity = Severity.HIGH;
					}
					report(t, child, severity, ReportActionType.INFO, "Inactivating child of " + c + extraInfo + ", prior to parent inactivation");
					doFix(t, child, " as descendant of " + c);
				} else {
					if (rewireChildrenToGrandparents) {
						rewireChildToGrandparents(t, child, c, parents);
					} else {
						//Otherwise, just remove the concept as a child of the parent
						removeParent(t, child, c);
					}
				}
			}
		}
		c.setActive(false);
		c.setEffectiveTime(null);
		
		String histAssocType = "Unknown Historical Association";
		if ((replacement== null || replacement.equals(NULL_CONCEPT)) && !inactivationIndicator.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			if (inactivationIndicator != null) {
				report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "File specified " + inactivationIndicator + " inactivation but no HistAssoc found. Switching to NCEP");
			}
			c.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			c.setAssociationTargets(new AssociationTargets());
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as 'NonConformance to Editorial Policy'");
		} else {
			c.setInactivationIndicator(inactivationIndicator);
			switch (inactivationIndicator) {
				case OUTDATED : c.setAssociationTargets(AssociationTargets.replacedBy(replacement));
								histAssocType = " replaced by ";
								break;
				case AMBIGUOUS : c.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
								histAssocType = " possibly equiv to ";
								break;
				case NONCONFORMANCE_TO_EDITORIAL_POLICY :	c.setAssociationTargets(new AssociationTargets());
															histAssocType = "";
															break;
				default : throw new TermServerScriptException("Unexpected inactivation indicator: " + inactivationIndicator);
			}
			
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as " + inactivationIndicator + histAssocType + (replacement.equals(NULL_CONCEPT)?"":replacement));
		}
		inactivations.put(c, t);
		return CHANGE_MADE;
	}

	private void rewireChildToGrandparents(Task t, Concept child, Concept parent, List<Concept> grandParents) throws TermServerScriptException {
		child = loadConcept(child, t.getBranchPath());
		report(t, child, Severity.MEDIUM, ReportActionType.INFO, "Rewiring child of " + parent + " to grandparents");
		
		List<Relationship> parentRels = child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		for (Relationship parentRel : parentRels) {
			if (parentRel.getTarget().equals(parent)) {
				removeRelationship(t, child, parentRel);
			}
		}
		
		//Remove any existing parents from list of new (grand)parents to add
		grandParents.removeAll(child.getParents(CharacteristicType.STATED_RELATIONSHIP));
		for (Concept grandParent : grandParents) {
			Relationship newParentRel = new Relationship (child, IS_A, grandParent, UNGROUPED);
			addRelationship(t,  child, newParentRel);
		}
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
			task.addAfter(child, parent);
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
		Concept originalTarget = gl.getConcept(assoc.getTargetComponentId());
		Concept incomingConcept = loadConcept(assoc.getReferencedComponentId(), task.getBranchPath());
		incomingConcept.setInactivationIndicator(reason);
		if (reason.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			incomingConcept.setAssociationTargets(null);
		} else if (reason.equals(InactivationIndicator.OUTDATED)) {
			incomingConcept.setAssociationTargets(AssociationTargets.replacedBy(replacement));
		} else if (reason.equals(InactivationIndicator.DUPLICATE)) {
			incomingConcept.setAssociationTargets(AssociationTargets.sameAs(replacement));
		} else if  (reason.equals(InactivationIndicator.AMBIGUOUS)) {
			incomingConcept.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
		} else {
			throw new IllegalArgumentException("Don't know what historical association to use with " + reason);
		}
		Severity severity = replacement == null ? Severity.CRITICAL : Severity.MEDIUM;
		report(task, incomingConcept, severity, ReportActionType.CONCEPT_CHANGE_MADE, "Historical association to " + originalTarget + " rewired to " + replacement);
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
		
		if (!c.isActive()) {
			report ((Task)null, c, Severity.NONE, ReportActionType.VALIDATION_CHECK, "Concept is already inactive.");
			incrementSummaryInformation("Skipped, already inactivated");
			return null;
		}
		
		Concept replacement = null;
		int idxReplacement = 1;
		
		/*if (lineItems.length > 2) {
			idxReplacement = 2;
			//In this case, column 1 will be in inactivation reason
			String strInact = lineItems[1];
			if (strInact.contains("Outdated")) {
				inactivationIndicators.put(c, InactivationIndicator.OUTDATED);
			} else if (strInact.contains("Nonconformance")) {
				inactivationIndicators.put(c, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			} else if (strInact.contains("Ambiguous")) {
				inactivationIndicators.put(c, InactivationIndicator.AMBIGUOUS);
			} else {
				warn ("Failed to identify inactivation indicator for: " + c);
			}
		}
		
		if (lineItems.length > 1) {
			if (lineItems[idxReplacement].toUpperCase().trim().equals("N/A")) {
				replacement = NULL_CONCEPT;
			} else {
				try {
					replacement = gl.getConcept(lineItems[idxReplacement]);
				} catch (Exception e) {
					if (expectReplacements) {
						warn ("Failed to identify replacement concept: " + lineItems[idxReplacement]);
					}
				}
			}
		}*/
		
		//Specific to IHTSDO-175
		inactivationIndicators.put(c, InactivationIndicator.AMBIGUOUS);
		replacement = gl.getConcept(" 782902008 |Implantation procedure (procedure)|");
		
		if (replacement != null) {
			replacements.put(c, replacement);
			return Collections.singletonList(c);
		} else if (!expectReplacements){
			return Collections.singletonList(c);
		}
		return null;
	}
}
