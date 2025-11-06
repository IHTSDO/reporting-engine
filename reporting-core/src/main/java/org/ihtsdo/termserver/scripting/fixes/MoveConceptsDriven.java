package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/*
 * INFRA-6069 List of organisms to move to Vet extension
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveConceptsDriven extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(MoveConceptsDriven.class);

	private String moveListFileStr = "G:/My Drive/020_BatchScripting/2020/infra-6069-mkii.txt";
	private String newLocationStr = "416516009|Extension Namespace {1000009} (namespace concept)|";
	private Set<String> moveList = new HashSet<>();
	private InactivationIndicator inactivationIndicator = InactivationIndicator.MOVED_ELSEWHERE;
	private Set<Concept> unsafeToProcess = new HashSet<>();
	private Map<Concept, Task> processed = new HashMap<>();
	
	protected MoveConceptsDriven(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		MoveConceptsDriven fix = new MoveConceptsDriven(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.populateTaskDescription = false;
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
	protected void init(String[] args) throws TermServerScriptException {
		try {
			File moveListFile = new File(moveListFileStr);
			for (String line : Files.readLines(moveListFile, Charsets.UTF_8)) {
				moveList.add(line.split(TAB)[0]);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failure while reading: " + moveListFileStr, e);
		}
		super.init(args);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = 0;
		if (loadedConcept == null || !loadedConcept.isActive()) {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Concept already inactivated?");
		} else if (loadedConcept.isReleased()) {
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
		
		//Check and inactivate any incoming historical associations
		for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
			inactivateHistoricalAssociation (t, assoc);
		}
		
		Concept newLocation = gl.getConcept(newLocationStr);
		c.setActive(false);  //Function also inactivates all relationships
		c.setEffectiveTime(null);
		c.setInactivationIndicator(inactivationIndicator);
		c.setAssociationTargets(AssociationTargets.movedTo(newLocation));
		
		String histAssocType = " moved to ";
		report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated as " + inactivationIndicator + histAssocType + " " + newLocation);
		return CHANGE_MADE;
	}
	
	private void inactivateHistoricalAssociation(Task t, AssociationEntry assoc) throws TermServerScriptException {
		//The source concept can no longer have this historical association, and its
		//inactivation reason must also change.
		Concept originalTarget = gl.getConcept(assoc.getTargetComponentId());
		Concept incomingConcept = loadConcept(assoc.getReferencedComponentId(), t.getBranchPath());
		incomingConcept.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		incomingConcept.setAssociationTargets(null);
		report(t, incomingConcept, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Historical association to " + originalTarget + " removed");
		//Add this concept into our task so we know it's been updated
		t.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
		updateConcept(t, incomingConcept, "");
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		Set<Concept> processMe = new LinkedHashSet<>();  //Order is important because descendants must be added first
		for (String sctId : moveList) {
			addComponentsToProcess(gl.getConcept(sctId), processMe);
		}
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		return new ArrayList<Component>(processMe);
	}
	
	private void addComponentsToProcess(Concept c, Set<Concept> processMe) throws TermServerScriptException {
		if (processMe.contains(c)) {
			return;
		}
		
		if (c.isActive()) {
			//Any descendants of that concept must also be moved
			for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
				//We need to recursively add the children first, so they're inactivated
				//before any parents.
				addComponentsToProcess(child, processMe);
				if (!moveList.contains(child.getId())) {
					report((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept has unexpected descendant adding to list", child);
				}
				
				Set<Concept> sources = getIncomingAttributeSources(c);
				if (sources.size() > 0) {
					report((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept is used as target of another concept", sources.iterator().next());
					unsafeToProcess.add(c);
					return;
				}
				processMe.add(child); //Inactivate descendants before parent
			}
			processMe.add(c);
		} else {
			report((Task)null, c, Severity.HIGH, ReportActionType.INFO, "Concept to move is inactive");
		}
	}

	private Set<Concept> getIncomingAttributeSources(Concept c) {
		Set<Concept> incomingAttributeSources = new HashSet<>();
		for (Concept source : gl.getAllConcepts()) {
			if (source.isActive()) {
				for (Relationship r : source.getRelationships()) {
					if (r.isActive() && !r.getType().equals(IS_A) && r.getTarget().equals(c)) {
						incomingAttributeSources.add(source);
					}
				}
			}
		}
		return incomingAttributeSources;
	}
}
