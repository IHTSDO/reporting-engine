package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * Removes a substring from all active Terms, where matched in context for a given subHierarchy
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveSubstringFromNewTerms extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveSubstringFromNewTerms.class);

	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static Map<String, String> replacementMap = new HashMap<String, String>();
	//static final String match = "mg/1 each oral tablet";
	static final String match = "milligram/1 each oral tablet";
	static final String remove = "/1 each";
	protected RemoveSubstringFromNewTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RemoveSubstringFromNewTerms fix = new RemoveSubstringFromNewTerms(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = removeWordsFromTerms(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}


	private int removeWordsFromTerms(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			if (d.getTerm().contains(match)) {
				String newTerm = d.getTerm().replace(remove, "");
				if (termAlreadyExists(concept, newTerm)) {
					report(task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: " + newTerm);
				} else if (d.getEffectiveTime() != null) {
					report(task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Term already published: " + d);
				} else {
					//To delete the description, we'll remove its SCTID and reuse the rest of the body for the new term
					d.setDescriptionId(null);
					d.setTerm(newTerm);
					changesMade++;
					report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Deleted description, replaced with " + d);
				}
			}
		}
		return changesMade;
	}

	private boolean termAlreadyExists(Concept concept, String newTerm) {
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions()) {
			if (description.getTerm().equals(newTerm)) {
				termAlreadyExists = true;
			}
		}
		return termAlreadyExists;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allPotential = GraphLoader.getGraphLoader().getConcept(subHierarchyStr).getDescendants(NOT_SET);
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		LOGGER.info("Identifying concepts to process");
		for (Concept c : allPotential) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().contains(match)) {
					allAffected.add(c);
					break;
				}
			}
		}
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		return new ArrayList<Component>(allAffected);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
