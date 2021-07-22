package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * ORGANISMS-1
 * Inactivate descriptions - driven by list.
 */
public class InactivateDescriptions extends BatchFix implements ScriptConstants {
	
	Map<Concept, List<Description>> inactivations = new HashMap<>();
	
	protected InactivateDescriptions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivateDescriptions fix = new InactivateDescriptions(null);
		try {
			fix.expectNullConcepts = true;
			fix.inputFileHasHeaderRow = false;
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
		int changesMade = modifyConcept(task, loadedConcept);
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
		}
		return changesMade;
	}
	
	private int modifyConcept(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//We might be inactivating more than one description for a concept
		if (!inactivations.containsKey(c) || inactivations.get(c).size() == 0) {
			throw new TermServerScriptException("Inactivations seem to have gotten lost for " + c);
		}
		
		for (Description dCached : inactivations.get(c)) {
			//This is our local cached copy, we need the object loaded from the TS
			for (Description d : c.getDescriptions()) {
				if (d.getId().equals(dCached.getId())) {
					if (d.isActive()) {
						removeDescription(t, c, d, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
						changesMade++;
					} else {
						report (t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Description already inactive: " + d);
					}
				}
			}
		}
		
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		String descriptionStr = lineItems[0];
		
		//We're expecting a description.toString() to be passed in, split it back out
		String[] elements = descriptionStr.split(" ");
		String conceptId = elements[1].replaceAll("\\[", "").replaceAll("\\]", "");
		Concept c = gl.getConcept(conceptId);
		//Find that description
		for (Description d : c.getDescriptions()) {
			if (d.getId().equals(elements[0])) {
				if (d.isActive()) {
					//Have we seen this concept before?
					if (inactivations.containsKey(c)) {
						inactivations.get(c).add(d);
					} else {
						List<Description> inactivationList = new ArrayList<>();
						inactivationList.add(d);
						inactivations.put(c, inactivationList);
					}
				} else {
					warn ("Already inactive: " + d);
					return null;
				}
				break;
			}
		}
		
		return Collections.singletonList(c);
	}
}
