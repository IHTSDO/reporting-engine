package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * QI-784 Inactivate concepts with the historical association being chosen as 
 * one of the existing parents of the concept.
 * QI-778 - Overdose of undetermined intent
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateConceptsAssocParent extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateConceptsAssocParent.class);

	private InactivationIndicator inactivationIndicator = InactivationIndicator.DUPLICATE;
	//private static String eclSubset = "<< 269736006 |Poisoning of undetermined intent (disorder)| ";
	private static String eclSubset = "<< 371341003 |Drug overdose of undetermined intent (disorder) OR 788105001 |Excessive dose of antiserum administered with undetermined intent (event)| OR 788101005 |Excessive dose of gamma globulin administered with undetermined intent (event)| OR 788097001 |Excessive dose of vaccine administered with undetermined intent (event)| OR 296528006 |Alternative medicine overdose of undetermined intent (navigational concept)| OR 295532006 |Ether overdose of undetermined intent (disorder)|";
	
	private Set<String> searchTerms = new HashSet<>();
	private Map<Concept, Task> processed = new HashMap<>();
	
	protected InactivateConceptsAssocParent(BatchFix clone) {
		super(clone);
	}
	
	@Override
	protected void onNewTask(Task task) {
		//See long winded explanation in InactivateConceptNoReplacement.java
		historicallyRewiredPossEquivTo.clear();
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateConceptsAssocParent fix = new InactivateConceptsAssocParent(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			//Cannot run stand alone as we need to know if the concept is published or not
			fix.runStandAlone = false;
			fix.maxFailures = Integer.MAX_VALUE;
			fix.reportNoChange = false;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		//INFRA-4865
		searchTerms.add("undetermined intent");
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		//Have we already seen this concept?
		if (processed.containsKey(c)) {
			//report(t, c, Severity.LOW, ReportActionType.INFO, "Concept already processed in " + processed.get(c));
			//We'll remove it from this task if it wasn't originally on this task
			if (!processed.get(c).equals(t)) {
				t.remove(c);
			}
			return NO_CHANGES_MADE;
		}
		
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = 0;
		if (loadedConcept == null || !loadedConcept.isActive()) {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Concept already inactivated?");
		} else if (loadedConcept.isReleased()) {
			changesMade = inactivateConcept(t, loadedConcept);
			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
		} else {
			changesMade = deleteConcept(t, loadedConcept);
		}
		processed.put(c, t);
		return changesMade;
	}
	
	private int inactivateConcept(Task t, Concept c) throws TermServerScriptException {
		Concept replacement = chooseParentForAssociation(t, c, false);
		Set<Concept> replacements = Collections.singleton(replacement);
		
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		//With the same inactivation reasons
		checkAndInactivatateIncomingAssociations(t, c, inactivationIndicator, replacements);
		
		//How many children do we have to also inactivate?  We've already checked they must all be.
		//Use locally held concept when traversing transitive closure
		Set<Concept> children = gl.getConcept(c.getConceptId()).getChildren(CharacteristicType.INFERRED_RELATIONSHIP);
		for (Concept child : children) {
			//This will call recursively and we'll add into this task
			if (doFix(t, child, null) > 0) {
				//report(t, child, Severity.LOW, ReportActionType.INFO, "Child inactivation squeezed into same task as " + c);
				t.remove(child);
				t.addAfter(child, c);
			}
		}
		
		//It might also be that our concept is used as the target of some other relationship, like an associated finding attribute
		//find all of these and check we're also expecting to inactivate the source of the incoming relationship
		Set<Concept> sources = getIncomingAttributeSources(c);
		for (Concept source : sources) {
			if (!allComponentsToProcess.contains(source)) {
				report(t, c, Severity.HIGH, ReportActionType.INFO, "Incoming attribute source not scheduled for inactivation.  Cannot inactivate concept", source);
				return NO_CHANGES_MADE; 
			}
			//This will call recursively and we'll add into this task
			if (doFix(t, source, null) > 0) {
				report(t, source, Severity.HIGH, ReportActionType.INFO, "Incoming attribute inactivation squeezed into same task as " + c);
				t.remove(source);
				t.addAfter(source, c);
			}
		}
		
		c.setActive(false);  //Function also inactivates all relationships
		c.setEffectiveTime(null);
		c.setInactivationIndicator(inactivationIndicator);
		c.setAssociationTargets(AssociationTargets.sameAs(replacement));
		String histAssocType = " same as ";
		report(t, c, Severity.LOW, ReportActionType.CONCEPT_INACTIVATED, "Concept inactivated as " + inactivationIndicator + histAssocType + (replacement.equals(NULL_CONCEPT)?"":replacement));
		return CHANGE_MADE;
	}


	
	private Concept chooseParentForAssociation(Task t, Concept c, boolean isFallBack) throws TermServerScriptException {
		//Use local copy of concept so model is fully populated
		c = gl.getConcept(c.getId());
		//Find the parent concept that does not contain our search terms
		Set<Concept> potentialReplacements = new HashSet<>();
		Set<Concept> fallbackReplacements = new HashSet<>();
		
		nextParent:
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			for (String searchTerm : searchTerms) {
				if (parent.getFsn().contains(searchTerm)) {
					if (!isFallBack) {
						//We'll only go one level up to find a fallback
						fallbackReplacements.add(chooseParentForAssociation(t, parent, true));
					}
					continue nextParent;
				}
			}
			potentialReplacements.add(parent);
		}
		if (isFallBack && potentialReplacements.size() == 0) {
			return null;
		}
		if (potentialReplacements.size() == 0) {
			if (fallbackReplacements.size() == 1) {
				Concept replacement = fallbackReplacements.iterator().next();
				report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Used grandparents to find suitable replacement", replacement);
				return replacement;
			} else if (fallbackReplacements.size() == 1) {
				String details = potentialReplacements.stream().map(p->p.getFsn()).collect(Collectors.joining(",\n"));
				report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Grandparents did not provide a single alternative", details);
			}
			throw new TermServerScriptException("Unable to find association replacement for " + c);
		} else if (potentialReplacements.size() > 1) {
			String details = potentialReplacements.stream().map(p->p.getFsn()).collect(Collectors.joining(",\n"));
			throw new TermServerScriptException(c + " identified multiple replacments : " + details);
		}
		return potentialReplacements.iterator().next();
	}

	private Set<Concept> getIncomingAttributeSources(Concept c) {
		Set<Concept> incomingAttributeSources = new HashSet<>();
		for (Concept source : gl.getAllConcepts()) {
			if (source.isActive()) {
				for (Relationship r : source.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (!r.getType().equals(IS_A) && r.getTarget().equals(c)) {
						incomingAttributeSources.add(source);
					}
				}
			}
		}
		return incomingAttributeSources;
	}

	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		//Check for this concept being the target of any existing relationships and rewire
		Concept replacement = chooseParentForAssociation(t, c, false);
		rewireRelationshipsUsingTargetValue(t, c, replacement);
		
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		Set<Concept> replacements = Collections.singleton(replacement);
		checkAndInactivatateIncomingAssociations(t, c, inactivationIndicator, replacements);
		report(t, c, Severity.MEDIUM, ReportActionType.CONCEPT_DELETED);
		return super.deleteConcept(t, c);
	}

	private void checkAndInactivatateIncomingAssociations(Task task, Concept c, InactivationIndicator reason, Set<Concept> replacements) throws TermServerScriptException {
		if (gl.usedAsHistoricalAssociationTarget(c).isEmpty()) {
			return;
		}
		
		for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
			Concept incoming = gl.getConcept(assoc.getReferencedComponentId());
			if (replacements == null || replacements.size() == 0) {
				String errMsg = "Concept is target of incoming historical association from " + incoming + " with no replacement suggested";
				throw new TermServerScriptException(errMsg);
			}
			inactivateHistoricalAssociation (task, assoc, c, reason, replacements);
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		return new ArrayList<Component>(findConcepts(eclSubset));
	}
	
}
