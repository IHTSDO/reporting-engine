package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;


/* INFRA-3449 Fix issue when the same relationship exists as both active and inactive
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateRelationships extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateRelationships.class);

	protected DuplicateRelationships(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DuplicateRelationships fix = new DuplicateRelationships(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.runStandAlone = false;  //Was causing issues with historical associations not being set
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); //Just 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeRedundantRelationships(task, tsConcept);
		if (changesMade > 0) {
			updateConcept(task, tsConcept, info);
		}
		return changesMade;
	}

	private int removeRedundantRelationships(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			Set<Relationship> duplicates = c.getRelationships(r, ActiveState.BOTH);
			if (duplicates.size() > 1) {
				//Has one been released?
				Set<Relationship> released = duplicates.stream().filter(l -> l.isReleased()).collect(Collectors.toSet());
				Relationship champion = null;
				if (released.size() == 1) {
					champion = released.iterator().next();
				} else if (released.size() > 1) {
					Severity severity = duplicates.size() == released.size() ? Severity.LOW : Severity.HIGH;
					report(t, c, severity, ReportActionType.VALIDATION_CHECK, "Concept has > 1 release duplicate relationships", r);
					t.remove(c);
					return NO_CHANGES_MADE;
				}
				duplicates.removeAll(released);
				for (Relationship duplicate : duplicates) {
					if (champion == null) {
						//If we haven't found one to keep yet, keep the first one.
						champion = duplicate;
					} else {
						changesMade += removeRelationship(t, c, duplicate);
					}
				}
				report(t, c, Severity.LOW, ReportActionType.INFO, "Retained: " + champion);
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find concepts with two relationships the same triple + group, but different Ids
		LOGGER.info("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		
		//Code to protect us from SCTIDs changing between released and TS files is 
		//Also preventing us from seeing duplicates, so I'll have to hard code the list
		
		processMe.add(gl.getConcept("127214002"));
		processMe.add(gl.getConcept("14114003"));
		processMe.add(gl.getConcept("197683002"));
		processMe.add(gl.getConcept("200893007"));
		processMe.add(gl.getConcept("201279004"));
		processMe.add(gl.getConcept("204702007"));
		processMe.add(gl.getConcept("204799005"));
		processMe.add(gl.getConcept("21346009"));
		processMe.add(gl.getConcept("234962001"));
		processMe.add(gl.getConcept("234963006"));
		processMe.add(gl.getConcept("238989000"));
		processMe.add(gl.getConcept("253291009"));
		processMe.add(gl.getConcept("253674003"));
		processMe.add(gl.getConcept("253829008"));
		processMe.add(gl.getConcept("41890004"));
		processMe.add(gl.getConcept("6111009"));
		processMe.add(gl.getConcept("67653003"));

		
		/*
		//for (Concept c : gl.getAllConcepts()) {
		for (Concept c : Collections.singletonList(gl.getConcept("14311001"))) {
			if (!c.isActive()) {
				continue;
			}
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (c.getRelationships(r, ActiveState.BOTH).size() > 1) {
					processMe.add(c);
				}
			}
		}
		*/
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}

}
