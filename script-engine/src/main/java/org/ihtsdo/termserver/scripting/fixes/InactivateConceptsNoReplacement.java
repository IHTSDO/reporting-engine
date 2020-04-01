package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * INFRA-4695
 * Inactivate concepts where a replacement does not exist, based on some critera
 * We will point the "MAY BE A" to the parent
 */
public class InactivateConceptsNoReplacement extends BatchFix implements RF2Constants {
	
	InactivationIndicator inactivationIndicator = InactivationIndicator.AMBIGUOUS;
	protected InactivateConceptsNoReplacement(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivateConceptsNoReplacement fix = new InactivateConceptsNoReplacement(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.init(args);
			fix.getArchiveManager().populateReleasedFlag = true;
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
				updateConcept(t, loadedConcept, info);
			}
		} else {
			changesMade = deleteConcept(t, loadedConcept);
		}
		return changesMade;
	}
	
	private int inactivateConcept(Task t, Concept c) throws TermServerScriptException {
		
		Set<Concept> replacements = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		//With the same inactivation reasons
		checkAndInactivatateIncomingAssociations(t, c, inactivationIndicator, replacements);
		
		//How many children do we have to also inactivate?  We've already checked they must all be.
		//Use locally held concept when traversing transitive closure
		Set<Concept> children = gl.getConcept(c.getConceptId()).getChildren(CharacteristicType.INFERRED_RELATIONSHIP);
		for (Concept child : children) {
			//This will call recursively and we'll add into this task
			if (doFix(t, child, null) > 0) {
				report(t, child, Severity.HIGH, ReportActionType.INFO, "Child inactivation squeezed into same task as " + c);
				t.addAfter(child, c);
			}
		}
		
		c.setActive(false);  //Function also inactivates all relationships
		c.setEffectiveTime(null);
		c.setInactivationIndicator(inactivationIndicator);
		c.setAssociationTargets(AssociationTargets.possEquivTo(replacements));
		
		String histAssocType = " possibly equiv to ";
		for (Concept replacement : replacements) {
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as " + inactivationIndicator + histAssocType + (replacement.equals(NULL_CONCEPT)?"":replacement));
		}
		return CHANGE_MADE;
	}


	
	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		checkAndInactivatateIncomingAssociations(t, c, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, null);
		report (t, c, Severity.MEDIUM, ReportActionType.CONCEPT_DELETED);
		return super.deleteConcept(t, c);
	}

	private void checkAndInactivatateIncomingAssociations(Task task, Concept c, InactivationIndicator reason, Set<Concept> replacements) throws TermServerScriptException {
		if (gl.usedAsHistoricalAssociationTarget(c) == null) {
			return;
		}
		for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
			inactivateHistoricalAssociation (task, assoc, reason, replacements);
		}
	}

	private void inactivateHistoricalAssociation(Task task, AssociationEntry assoc, InactivationIndicator reason, Set<Concept> replacements) throws TermServerScriptException {
		//The source concept can no longer have this historical association, and its
		//inactivation reason must also change.
		Concept originalTarget = gl.getConcept(assoc.getTargetComponentId());
		Concept incomingConcept = loadConcept(assoc.getReferencedComponentId(), task.getBranchPath());
		incomingConcept.setInactivationIndicator(reason);
		if (reason.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			incomingConcept.setAssociationTargets(null);
		} else if (reason.equals(InactivationIndicator.OUTDATED)) {
			throw new NotImplementedException();
			//incomingConcept.setAssociationTargets(AssociationTargets.replacedBy(replacements));
		} else if (reason.equals(InactivationIndicator.DUPLICATE)) {
			throw new NotImplementedException();
			//incomingConcept.setAssociationTargets(AssociationTargets.sameAs(replacements));
		} else if  (reason.equals(InactivationIndicator.AMBIGUOUS)) {
			incomingConcept.setAssociationTargets(AssociationTargets.possEquivTo(replacements));
		} else {
			throw new IllegalArgumentException("Don't know what historical association to use with " + reason);
		}
		Severity severity = replacements.size() == 0 ? Severity.CRITICAL : Severity.MEDIUM;
		for (Concept replacement : replacements) {
			report(task, incomingConcept, severity, ReportActionType.CONCEPT_CHANGE_MADE, "Historical association to " + originalTarget + " rewired to " + replacement);
		};
			//Add this concept into our task so we know it's been updated
		task.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
		updateConcept(task, incomingConcept, "");
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		info ("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		Concept occupation = gl.getConcept("14679004 |Occupation (occupation)|");
		nextConcept:
		for (Concept c : occupation.getDescendents(NOT_SET)) {
		//for (Concept c : Collections.singleton(gl.getConcept("347118002"))) {
			if (c.isActive() && c.getFsn().startsWith("Other")) {
				//Any children of that concept must also require inactivating, or we have to 
				//do more work to re-point them (see grandchildren rewiring in sibling class)
				for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
					if (!child.getFsn().startsWith("Other")) {
						//throw new TermServerScriptException ( c + " has child not scheduled for deletion");
						report ((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept has children not being inactived. Process manually.");
						continue nextConcept;
					}
				}
				processMe.add(c);
			}
		}
		info ("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}
}
