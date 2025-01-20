package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
DRUGS-593 Remove FSN Counterparts ie synonym whihc is the FSN without the semantic tag
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveFSNCounterparts extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveFSNCounterparts.class);

	Concept subHierarchy = MEDICINAL_PRODUCT;
	
	protected RemoveFSNCounterparts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RemoveFSNCounterparts fix = new RemoveFSNCounterparts(null);
		try {
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeFSNCounterpart(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, info);
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int removeFSNCounterpart(Task t, Concept c) throws TermServerScriptException {
		Description counterpart = findCounterpart(c);
		Boolean isDeleted = false;
		if (counterpart.isReleased()) {
			counterpart.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			counterpart.setActive(false);
		} else {
			c.removeDescription(counterpart);
			isDeleted = true;
		}
		report(t, c , Severity.LOW, isDeleted?ReportActionType.DESCRIPTION_DELETED:ReportActionType.DESCRIPTION_INACTIVATED, counterpart);
		return CHANGE_MADE;
	}


	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");

		List<Concept> processMe = new ArrayList<>();
		for (Concept c : subHierarchy.getDescendants(NOT_SET)) {
			if (c.getConceptId().equals("317265007")) {
				//LOGGER.debug("LOGGER.debug here!");
			}
			if (c.getFsn().contains("(clinical drug)") && findCounterpart(c) != null) {
				processMe.add(c);
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return asComponents(processMe);
	}

	private Description findCounterpart(Concept c) {
		String fsnCounterpartStr = SnomedUtils.deconstructFSN(c.getFsn())[0];
		Description counterpart = c.findTerm(fsnCounterpartStr);
		if (counterpart != null) {
			return counterpart.isActive() ? counterpart : null;
		}
		return null;
	}

}
