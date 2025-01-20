package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/* MAINT-489 Sets an inactivation indicator against all concepts with a given semantic tag
 * We will usually use "Non Conformance to Editorial Policy" as the indicator, since no
 * historical association is required in this case.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MissingInactivationIndicatorsFix extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingInactivationIndicatorsFix.class);

	String targetSemanticTag = "(product)";
	
	protected MissingInactivationIndicatorsFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		MissingInactivationIndicatorsFix fix = new MissingInactivationIndicatorsFix(null);
		try {
			fix.runStandAlone = false;  //Was causing issues with historical associations not being set
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); //Just 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (tsConcept.isActive()) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active. Won't be adding inactivation indicators to descriptions.");
		} else {
			changesMade = fixInactivationIndicator(task, tsConcept);
			if (changesMade > 0) {
				updateConcept(task, tsConcept, info);
			}
		}
		return changesMade;
	}

	private int fixInactivationIndicator(Task t, Concept c) throws TermServerScriptException {
		if (c.getInactivationIndicator() == null) {
			c.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			report(t, c, Severity.LOW, ReportActionType.INACT_IND_ADDED, "Added NCEP Indicator");
			return CHANGE_MADE;
		}
		
		if (c.getAssociationTargets().size() == 0) {
			InactivationIndicator prev = c.getInactivationIndicator();
			c.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			String msg = "Inactivation indicator changed from " + prev + " to " + c.getInactivationIndicator();
			report(t, c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, msg);
			return CHANGE_MADE;
		}
		
		throw new ValidationFailure (c, "Failed to find reason to make change");
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		LOGGER.info("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
		//for (Concept c : Collections.singleton(gl.getConcept("347118002"))) {
			if (!c.isActive()) {
				String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				if (semTag != null && semTag.equals(targetSemanticTag) && hasIssue(c)) {
					processMe.add(c);
				}
			}
		}
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}

	private boolean hasIssue(Concept c) {
		//Issues would be not having an inactivation indicator
		//Revoked - historically we allowed this
		/*if (c.getInactivationIndicator() == null) {
			return true;
		}*/
		
		//or having some indicator other than NCEP with no historical associations
		if (c.getInactivationIndicator() != null && !c.getInactivationIndicator().equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY) &&
				c.getAssociationEntries(ActiveState.ACTIVE, true).size() == 0) {
			return true;
		}
		
		return false;
	}

}
