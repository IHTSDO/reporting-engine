package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * ISRS-391, ISRS-392
 * Where a description or relationship belongs to a module that differs
 * from that of the host concept, correct that.
 * Applies to both active and inactive concepts.
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorrectModuleId extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(CorrectModuleId.class);

	protected CorrectModuleId(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CorrectModuleId fix = new CorrectModuleId(null);
		try {
			ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ");  //Release QA
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		//We will not load the concept because the Browser endpoint does not populate the full array of inactivation indicators
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = fixModuleId(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException ("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixModuleId(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions()) {
			if (!d.getModuleId().equals(c.getModuleId())) {
				report(t, c, Severity.LOW, ReportActionType.MODULE_CHANGE_MADE, d, d.getModuleId() + " -> " + c.getModuleId());
				d.setModuleId(c.getModuleId());
				changesMade++;
			}
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			if (!r.getModuleId().equals(c.getModuleId())) {
				report(t, c, Severity.LOW, ReportActionType.MODULE_CHANGE_MADE, r, r.getModuleId() + " -> " + c.getModuleId());
				r.setModuleId(c.getModuleId());
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		LOGGER.info("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		setQuiet(true);
		for (Concept c : ROOT_CONCEPT.getDescendants(NOT_SET)) {
			if (fixModuleId(null, c.cloneWithIds()) > 0) {
				processMe.add(c);
			}
		}
		setQuiet(false);
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<>(processMe);
	}

}
