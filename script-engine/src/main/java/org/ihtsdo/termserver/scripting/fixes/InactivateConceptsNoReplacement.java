package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.io.Files;

/*
 * INFRA-4695
 * Inactivate concepts where a replacement does not exist, based on some critera
 * We will point the "MAY BE A" to the parent
 * 
 * INFRA-4865 Hist assoc will be MOVED TO -> UK
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateConceptsNoReplacement extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateConceptsNoReplacement.class);

	InactivationIndicator inactivationIndicator = InactivationIndicator.MOVED_ELSEWHERE;
	
	Set<String> searchTerms = new HashSet<>();
	Set<Concept> targetHierarchies = new HashSet<>();
	Map<Concept, Set<Concept>> incomingHistAssocReplacements;
	Set<Concept> unsafeToProcess = new HashSet<>();
	Map<Concept, Task> processed = new HashMap<>();
	
	//This is tricky to explain.  I will attempt:
	//When we inactivate a concept, it might be the target of an existing incoming historical association.
	//These need to be rewired to point to some alternative
	//However, the source of that association might also point to some OTHER concept that we're
	//inactivating, in which case we need to leave that rewiring in place ie for a single given concept
	//we can't just wipe out all existing historical associations.  We need to maintain a new set of them.
	
	protected InactivateConceptsNoReplacement(BatchFix clone) {
		super(clone);
	}
	
	@Override
	protected void onNewTask(Task task) {
		//See long winded explanation above
		historicallyRewiredPossEquivTo.clear();
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateConceptsNoReplacement fix = new InactivateConceptsNoReplacement(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.maxFailures = Integer.MAX_VALUE;
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
	public void postInit() throws TermServerScriptException {
		//INFRA-4865
		searchTerms.add("on examination");
		searchTerms.add("o/e");
		searchTerms.add("complaining of");
		searchTerms.add("c/o");
		targetHierarchies.add(CLINICAL_FINDING);
		targetHierarchies.add(SITN_WITH_EXP_CONTXT);
		
		super.postInit();
		
		//Need report output initialised before this next bit
		if (getInputFile() != null) {
			loadIncomingHistoricReplacements();
		}
	}

	private void loadIncomingHistoricReplacements() throws TermServerScriptException {
		incomingHistAssocReplacements = new HashMap<>();
		print("Loading Historic Replacements " + getInputFile() + "...");
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException("Cannot read: " + getInputFile());
		}
		List<String> lines;
		try {
			lines = Files.readLines(getInputFile(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Failure while reading: " + getInputFile(), e);
		}
		LOGGER.debug("Processing Historic Replacements File");
		for (String line : lines) {
			//Split the line up on tabs
			String[] items = line.split(TAB);
			//We're only populating where we have a replacements
			if (items.length >= 3) {
				for (int replacementIdx = 2; replacementIdx < items.length; replacementIdx++) {
					Concept inactivatedConcept = gl.getConcept(items[0], false, true);
					String replacementSCTID = items[replacementIdx];
					
					//Does it even have an SCTID in it?   Try a description look up if not
					if (!SnomedUtils.startsWithSCTID(replacementSCTID)) {
						Concept conceptFromDesc = gl.findConcept(replacementSCTID);
						if (conceptFromDesc != null) {
							replacementSCTID = conceptFromDesc.getConceptId();
							LOGGER.warn("Looked up replacement {}", conceptFromDesc);
						} else {
							report((Task)null, inactivatedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN of replacement doesn't exist in current environment. ", replacementSCTID);
							continue;
						}
					}
					
					Concept replacement = gl.getConcept(replacementSCTID, false, false);
					if (replacement == null) {
						report((Task)null, inactivatedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "suggested replacement doesn't exist in current environment. ", replacementSCTID);
					} else {
						Set<Concept> replacements = incomingHistAssocReplacements.get(inactivatedConcept);
						if (replacements == null) {
							replacements = new HashSet<>();
							incomingHistAssocReplacements.put(inactivatedConcept, replacements);
						}
						replacements.add(replacement);
					}
				}
			}
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = 0;
		if (loadedConcept == null || !loadedConcept.isActive()) {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Concept already inactivated?");
		} else if (loadedConcept.isReleasedSafely()) {
			changesMade = inactivateConcept(t, loadedConcept);
			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
			processed.put(c, t);
		} else {
			changesMade = deleteConcept(t, loadedConcept);
		}
		return changesMade;
	}
	
	private int inactivateConcept(Task t, Concept c) throws TermServerScriptException {
		//Have we already seen this concept?
		if (processed.containsKey(c)) {
			report(t, c, Severity.LOW, ReportActionType.INFO, "Concept already processed in " + processed.get(c));
			//We'll remove it from this task if it wasn't originally on this task
			if (!processed.get(c).equals(t)) {
				t.remove(c);
			}
			return NO_CHANGES_MADE;
		}
		
		//Set<Concept> replacements = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		Concept replacement = gl.getConcept("370137002|Extension Namespace {1000000} (namespace concept)|");
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
				report(t, child, Severity.LOW, ReportActionType.INFO, "Child inactivation squeezed into same task as " + c);
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
		
		c.setAssociationTargets(AssociationTargets.movedTo(replacement));
		
		String histAssocType = " moved to ";
		report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as " + inactivationIndicator + histAssocType + (replacement.equals(NULL_CONCEPT)?"":replacement));
		return CHANGE_MADE;
	}


	
	private Set<Concept> getIncomingAttributeSources(Concept c) {
		Set<Concept> incomingAttributeSources = new HashSet<>();
		for (Concept source : gl.getAllConcepts()) {
			if (source.isActiveSafely()) {
				for (Relationship r : source.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (!r.getType().equals(IS_A) && r.getTarget().equals(c)) {
						incomingAttributeSources.add(source);
					}
				}
			}
		}
		return incomingAttributeSources;
	}

	@Override
	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		//Check for this concept being the target of any historical associations and rewire them to the replacement
		checkAndInactivatateIncomingAssociations(t, c, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, null);
		report(t, c, Severity.MEDIUM, ReportActionType.CONCEPT_DELETED);
		return super.deleteConcept(t, c);
	}

	private void checkAndInactivatateIncomingAssociations(Task task, Concept c, InactivationIndicator reason, Set<Concept> replacements) throws TermServerScriptException {
		if (gl.usedAsHistoricalAssociationTarget(c).isEmpty()) {
			return;
		}
		
		
		for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
			Concept incoming = gl.getConcept(assoc.getReferencedComponentId());
			if (incoming.getId().equals("140506004")) {
				LOGGER.debug("here");
			}
			//Does the concept we're inactivating have a concepts designated to rewire to?
			if (incomingHistAssocReplacements.containsKey(c)) {
				replacements = incomingHistAssocReplacements.get(c);
				//For INFRA-4865 we can't re-jig the incoming associations, so we'll use AMBIGUOUS
				//so the suggested replacement is PossEquivTo
				reason = InactivationIndicator.AMBIGUOUS;
				inactivateHistoricalAssociation (task, assoc, c, reason, replacements);
			} else {
				String errMsg = "Concept is target of incoming historical association from " + incoming + " with no replacement suggested";
				throw new TermServerScriptException(errMsg);
			}
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		Set<Concept> processMe = new HashSet<>();
		
		for (Concept hierarchy : targetHierarchies) {
			for (Concept c : hierarchy.getDescendants(NOT_SET)) {
				addComponentsToProcess(c, processMe);
			}
		}
		LOGGER.info("Identified {} concepts to process", processMe.size());
		return new ArrayList<>(processMe);
	}
	
	private void addComponentsToProcess(Concept c, Set<Concept> processMe) throws TermServerScriptException {
		if (c.isActiveSafely() && containsSearchTerm(c)) {
			if (!isSafeToInactivate(c)) {
				report((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept has incoming historical association and no replacement.");
				unsafeToProcess.add(c);
				return;
			}
			
			//Use a 2nd collection to hold descendants temporarily, until we know they're all safe
			//If any descendant concept is unsafe, we cannot inactivate the ancestor
			Set<Concept> descendantsToProcess = new HashSet<>();
			//Any children of that concept must also require inactivating, or we have to 
			//do more work to re-point them (see grandchildren rewiring in sibling class)
			for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (!containsSearchTerm(child)) {
					report((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept has child not being inactived due to absence of lexical indicator. Process manually.", child);
					unsafeToProcess.add(child);
					return;
				}
				
				if (!isChildSafeToInactivate(child)) {
					report((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept has child not being inactived due to incoming historical assocation without replacement. Process manually.", child);
					unsafeToProcess.add(child);
					return;
				}
				addComponentsToProcess(child, descendantsToProcess);
			}
			processMe.add(c);
			processMe.addAll(descendantsToProcess);
		}
		
	}

	private boolean isChildSafeToInactivate(Concept child) throws TermServerScriptException {
		//Do we already know this concept is unsafe to process?
		if (unsafeToProcess.contains(child)) {
			report((Task) null, child, Severity.HIGH, ReportActionType.NO_CHANGE, "Concept previously identified as unsafe to process via another ancestory");
			return false;
		}
		
		//If we have further children, we have to check all of them as well
		for (Concept grandchild : child.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (!isChildSafeToInactivate(grandchild)) {
				report((Task) null, child, Severity.HIGH, ReportActionType.NO_CHANGE, "Descendant concept is not safe to inactivate", grandchild);
				return false;
			}
		}
		
		if (isSafeToInactivate(child)) {
			return true;
		}
		
		Concept example = gl.getConcept(gl.usedAsHistoricalAssociationTarget(child).get(0).getReferencedComponentId());
		report((Task) null, child, Severity.NONE, ReportActionType.INFO, "Example of incoming historical association ", example);
		return false;
	}


	private boolean isSafeToInactivate(Concept c) {
		//To be safe this concept has to have a replacement for incoming historical associations,
		//or no incoming historical associations
		return incomingHistAssocReplacements.containsKey(c) || gl.usedAsHistoricalAssociationTarget(c).isEmpty();

	}

	private boolean containsSearchTerm(Concept c) {
		String term = c.getFsn().toLowerCase();
		for (String searchTerm : searchTerms) {
			if (term.contains(searchTerm)) {
				return true;
			}
		}
		return false;
	}
}
