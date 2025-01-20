package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
Assertion Failure fix checks a number of known assertion issues and makes
changes to the concepts if required
 */
public class AssertionFailureFix extends BatchFix implements ScriptConstants{
	
	protected AssertionFailureFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		AssertionFailureFix fix = new AssertionFailureFix(null);
		try {
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); //Load FSNs only
			//We won't incude the project export in our timings
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = fixAssertionIssues(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int fixAssertionIssues(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		changesMade += ensureTerms(task, loadedConcept);
		return changesMade;
	}

	private int ensureTerms(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		//TODO Need to add check that all components have been published before
		//inactivating.  If NOT published, should delete and recreate.
		//Loop through all the terms for the concept and for any active one:
		// 1. replace any double spaces with single spaces
		// 2. TBA
		//Then inactivate that term and replace with an otherwise identical one.
		List<Description> originalDescriptions = new ArrayList<Description>(concept.getDescriptions());
		for (Description d : originalDescriptions) {
			if (d.isActive()) {
				String newTerm = d.getTerm().replaceAll("    ", " ").replaceAll("   ", " ").replaceAll("  ", " ");
				if (!newTerm.equals(d.getTerm())) {
					Description replacement = d.clone(null);
					replacement.setTerm(newTerm);
					changesMade++;
					concept.addDescription(replacement);
					d.setActive(false);
					d.setEffectiveTime(null);
					d.setInactivationIndicator(InactivationIndicator.RETIRED);
					String msg = "Replaced term '" + d.getTerm() + "' with '" + replacement.getTerm() + "'.";
					report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
				}
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[2]);
		c.setAssignedAuthor(lineItems[0]);
		c.setReviewer(lineItems[1]);
		c.addAssertionFailure(lineItems[3]);
		return Collections.singletonList(c);
	}

}
