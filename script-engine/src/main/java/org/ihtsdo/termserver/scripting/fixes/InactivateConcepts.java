package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

public class InactivateConcepts extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateConcepts.class);

	private static final String HIST_ASSOC_TO = "Historical association to ";

	private InactivationIndicator defaultInactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
	
	Map<Concept, Concept> replacements = new HashMap<>();
	Map<Concept, InactivationIndicator> inactivationIndicators = new HashMap<>();
	Map<Concept, Task> inactivations = new HashMap<>();
	boolean expectReplacements = false;
	boolean autoInactivateChildren = false;
	boolean rewireChildrenToGrandparents = false;

	List<Concept> exceptions = new ArrayList<>();
	
	protected InactivateConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateConcepts fix = new InactivateConcepts(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.inputFileHasHeaderRow = false;
			fix.expectNullConcepts = false;
			fix.groupByIssue = false;
			fix.reportNoChange = false;
			fix.selfDetermining = true;
			fix.subsetECL = "<< 363743006 |Navigational concept (navigational concept)|";
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
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
		} else if (loadedConcept.isReleasedSafely()) {
			changesMade = inactivateConcept(t, loadedConcept);
			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
		} else {
			if (!exceptions.contains(c)) {
				changesMade = deleteConcept(t, loadedConcept);
			} else {
				report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Concept saved from deletion by exception list");
			}
		}
		return changesMade;
	}
	
	private int inactivateConcept(Task t, Concept c) throws TermServerScriptException {
		//Do we have a replacement for this concepts?  
		Concept replacement = replacements.get(c);
		if (expectReplacements && replacement == null) {
			throw new ValidationFailure(c, "Unable to inactivate without replacement");
		}
		//Is this concept an exception, that we're not going to inactivate?
		//We're still going to inactivate its children if so
		boolean isException = exceptions.contains(c);
		
		//Have we already inactivated this concept?
		if (inactivations.containsKey(c)) {
			//If it was inactivated already in THIS task, then no need to report that.
			if (!inactivations.get(c).equals(t)) {
				report(t, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Concept already inactivated in " + inactivations.get(c).getKey());
			}
			return NO_CHANGES_MADE;
		}

		InactivationIndicator inactivationIndicator = inactivationIndicators.get(c);
		if (inactivationIndicator == null) {
			inactivationIndicator = defaultInactivationIndicator;
		}

		if (!isException) {
			//Check for this concept being the target of any historical associations and rewire them to the replacement
			//With the same inactivation reasons
			checkAndInactivatateIncomingAssociations(t, c, inactivationIndicator, replacement);
		}

		//How many children do we have to do something different with?
		//Use locally held concept when traversing transitive closure
		Set<Concept> descendants = gl.getConcept(c.getConceptId()).getDescendants(NOT_SET, CharacteristicType.STATED_RELATIONSHIP);
		descendants.removeAll(allComponentsToProcess);
		if (!descendants.isEmpty()) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Inactivated concept has " + descendants.size() + " descendants not scheduled for inactivation");
		}
		
		processChildren(t, c, inactivationIndicator, replacement);

		if (!isException) {
			doConceptInactivation(t, c, inactivationIndicator, replacement);
		} else {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Concept saved from inactivation by exception list");
		}

		return CHANGE_MADE;
	}

	private void doConceptInactivation(Task t, Concept c, InactivationIndicator inactivationIndicator,
			Concept replacement) throws TermServerScriptException {
		c.setActive(false);
		c.setEffectiveTime(null);

		String histAssocType;
		if ((replacement == null || replacement.equals(NULL_CONCEPT)) && !inactivationIndicator.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "File specified " + inactivationIndicator + " inactivation but no HistAssoc found. Switching to NCEP");
			c.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			c.setAssociationTargets(new AssociationTargets());
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as 'NonConformance to Editorial Policy'");
		} else {
			c.setInactivationIndicator(inactivationIndicator);
			switch (inactivationIndicator) {
				case OUTDATED:
					c.setAssociationTargets(AssociationTargets.replacedBy(replacement));
					histAssocType = " replaced by ";
					break;
				case AMBIGUOUS:
					c.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
					histAssocType = " possibly equiv to ";
					break;
				case NONCONFORMANCE_TO_EDITORIAL_POLICY:
					c.setAssociationTargets(new AssociationTargets());
					histAssocType = "";
					break;
				default:
					throw new TermServerScriptException("Unexpected inactivation indicator: " + inactivationIndicator);
			}

			if (replacement != null && !replacement.equals(NULL_CONCEPT)) {
				report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as " + inactivationIndicator + histAssocType + replacement);
			} else {
				report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as " + inactivationIndicator);
			}
		}
		inactivations.put(c, t);
	}

	private void processChildren(Task t, Concept c, InactivationIndicator inactivationIndicator, Concept replacement) throws TermServerScriptException {
		Set<Concept> parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		//Check for any stated children and remove this concept as a parent
		for (Concept child : gl.getConcept(c.getConceptId()).getDescendants(IMMEDIATE_CHILD, CharacteristicType.STATED_RELATIONSHIP)) {
			if (exceptions.contains(child)) {
				LOGGER.warn("Child {} is an exception to inactivation, but parent being inactivated.  Needs rewired to grandparent or higher?", child);
				continue;
			}
			//Have we already inactivated this child?
			if (!inactivations.containsKey(child)) {
				t.remove(child);
				t.addAfter(child, c);
				removeFromLaterTasks(t, child);
				inactivateChild(t, child, c, inactivationIndicator, replacement, parents);
			}
		}
	}

	private void inactivateChild(Task t, Concept child, Concept c, InactivationIndicator inactivationIndicator,
			Concept replacement, Set<Concept> parents) throws TermServerScriptException {
		//Is this a concept we have to inactivate?  Go through whole process if so
		if (replacements.containsKey(child) || autoInactivateChildren || allComponentsToProcess.contains(child)) {
			String extraInfo = " as per selection";
			Severity severity = Severity.LOW;
			if (!replacements.containsKey(child) && !allComponentsToProcess.contains(child)) {
				//In this case, we should inactivate the child with the same details as the parent
				inactivationIndicators.put(child, inactivationIndicator);
				replacements.put(child, replacement);
				extraInfo = " NOT SPECIFIED FOR INACTIVATION";
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

	private void rewireChildToGrandparents(Task t, Concept child, Concept parent, Set<Concept> grandParents) throws TermServerScriptException {
		int changesMade = 0;
		child = loadConcept(child, t.getBranchPath());
		report(t, child, Severity.MEDIUM, ReportActionType.INFO, "Rewiring child of " + parent + " to grandparents");
		
		Set<Relationship> parentRels = child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		for (Relationship parentRel : parentRels) {
			if (parentRel.getTarget().equals(parent)) {
				changesMade += removeRelationship(t, child, parentRel);
			}
		}
		
		//Remove any existing parents from list of new (grand)parents to add
		grandParents.removeAll(child.getParents(CharacteristicType.STATED_RELATIONSHIP));
		for (Concept grandParent : grandParents) {
			Relationship newParentRel = new Relationship (child, IS_A, grandParent, UNGROUPED);
			addRelationship(t,  child, newParentRel);
			changesMade += CHANGE_MADE;
		}
		
		if (changesMade > 0) {
			updateConcept(t, child, "");
			t.addAfter(child, parent);
		} else {
			report(t, child, Severity.CRITICAL, ReportActionType.API_ERROR, "Did not rewire " + child + " as child of " + parent + ".  Please investigate.");
		}
	}

	private void removeParent(Task task, Concept child, Concept parent) throws TermServerScriptException {
		
		child = loadConcept(child, task.getBranchPath());
		//Check we've got more than one stated parent!
		Set<Relationship> parentRels = child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
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
			updateConcept(task, child, "");
			task.addAfter(child, parent);
		} else {
			report(task, child, Severity.HIGH, ReportActionType.API_ERROR, "Did not remove " + parent + " as parent of " + child);
		}
	}
	
	@Override
	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		checkAndInactivatateIncomingAssociations(t, c, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, null);
		report(t, c, Severity.MEDIUM, ReportActionType.CONCEPT_DELETED);
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

		//Do we need to align the incoming concept's inactivation indicator?
		boolean iiChanged = false;
		InactivationIndicator origII = incomingConcept.getInactivationIndicator();
		if (origII == null) {
			//If we didn't previously have an inactivation indicator, set it to NCEP - Non Conformance to Editorial Policy
			//UNLESS we detect NOS or Other, in which case we'll use CDC - Classification Derived Component
			boolean setCDC = checkForNOSOrOther(incomingConcept);
			reason = setCDC ? InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT : InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
			incomingConcept.setInactivationIndicator(reason);
			iiChanged = true;
			report(task, incomingConcept, Severity.HIGH, ReportActionType.INACT_IND_MODIFIED, "No II on incoming association.  Setting to: " + reason);
		} else if (!origII.equals(reason)) {
			if (origII.equals(InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT)) {
				report(task, incomingConcept, Severity.MEDIUM, ReportActionType.NO_CHANGE, origII + " left unchanged");
			} else {
				incomingConcept.setInactivationIndicator(reason);
				iiChanged = true;
				report(task, incomingConcept, Severity.MEDIUM, ReportActionType.INACT_IND_MODIFIED, origII + " --> " + reason);
			}
		}

		boolean conceptUpdateNeeded = setIncomingHistoricalAssociationsDependingOnInactivationIndicator(task, assoc, reason, replacement, originalTarget, incomingConcept, iiChanged);
		if (conceptUpdateNeeded) {
			Severity severity = replacement == null ? Severity.CRITICAL : Severity.MEDIUM;
			report(task, incomingConcept, severity, ReportActionType.CONCEPT_CHANGE_MADE, HIST_ASSOC_TO + originalTarget + " rewired to " + replacement);
			//Add this concept into our task so we know it's been updated
			task.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
			updateConcept(task, incomingConcept, "");
		}
	}

	/**
	 * @return true if the concept still needs to be saved
	 */
	private boolean setIncomingHistoricalAssociationsDependingOnInactivationIndicator(Task task, AssociationEntry assoc, InactivationIndicator reason, Concept replacement, Concept originalTarget, Concept incomingConcept, boolean iiChanged) throws TermServerScriptException {
		if (reason.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)
				|| reason.equals(InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT)) {
			//If we're modifying the refset member directly, we also need to save the concept to pick up the ii change
			if (iiChanged) {
				updateConcept(task, incomingConcept, "");
			}

			//In this case the replacement is expected to be null, and we will just inactivate that historical association
			if (assoc.isReleasedSafely()) {
				assoc.setActive(false);
				updateRefsetMember(task, assoc, "");
				report(task, incomingConcept, Severity.MEDIUM, ReportActionType.REFSET_MEMBER_REMOVED, HIST_ASSOC_TO + originalTarget + " inactivated with no replacement");
				task.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
			} else {
				deleteRefsetMember(task, assoc.getId());
				report(task, incomingConcept, Severity.MEDIUM, ReportActionType.REFSET_MEMBER_REMOVED, HIST_ASSOC_TO + originalTarget + " inactivated with no deleted");
				task.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
			}
			return false;
		} else if (reason.equals(InactivationIndicator.OUTDATED)) {
			incomingConcept.setAssociationTargets(AssociationTargets.replacedBy(replacement));
		} else if (reason.equals(InactivationIndicator.DUPLICATE)) {
			incomingConcept.setAssociationTargets(AssociationTargets.sameAs(replacement));
		} else if  (reason.equals(InactivationIndicator.AMBIGUOUS)) {
			incomingConcept.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
		} else {
			throw new IllegalArgumentException("Don't know what historical association to use with " + reason);
		}
		return true;
	}

	private boolean checkForNOSOrOther(Concept c) {
		return c.getFsn().contains("NOS")
				|| c.getFsn().contains("NEC")
				|| c.getFsn().contains("Other");
	}


	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c;
		if (StringUtils.isNumeric(lineItems[0])) {
			c = gl.getConcept(lineItems[0]);
		} else {
			c = gl.findConcept(lineItems[0]);
		}
		
		if (!c.isActiveSafely()) {
			report((Task)null, c, Severity.NONE, ReportActionType.VALIDATION_CHECK, "Concept is already inactive.");
			incrementSummaryInformation("Skipped, already inactivated");
			return Collections.emptyList();
		}
		return processLineForConcept(c, lineItems);
	}

	private List<Component> processLineForConcept(Concept c, String[] lineItems) {
		Concept replacement = null;
		int idxReplacement = 1;
		if (lineItems.length > 2) {
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
				LOGGER.warn("Failed to identify inactivation indicator for: {}", c);
			}
		}

		if (lineItems.length > 1) {
			replacement = determineReplacementFromInputLine(lineItems, idxReplacement);
		}

		//Give C an issue of one of its parents to try to batch sibling concepts together
		c.addIssue(c.getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next().getId());

		if (replacement != null) {
			replacements.put(c, replacement);
			return Collections.singletonList(c);
		} else if (!expectReplacements){
			return Collections.singletonList(c);
		}
		return Collections.emptyList();
	}

	private Concept determineReplacementFromInputLine(String[] lineItems, int idxReplacement) {
		if (!lineItems[idxReplacement].toUpperCase().trim().equals("N/A")) {
			try {
				return gl.getConcept(lineItems[idxReplacement]);
			} catch (Exception e) {
				if (expectReplacements) {
					LOGGER.warn("Failed to identify replacement concept: {}", lineItems[idxReplacement]);
				}
			}
		}
		return NULL_CONCEPT;
	}
}
