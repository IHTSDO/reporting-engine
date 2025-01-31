package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * ORGANISMS-1
 * Inactivate descriptions - driven by list.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateDescriptions extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateDescriptions.class);

	enum Mode { BY_DESCRIPTION_ID, BY_TERM_PLUS_LANG}

	private static final Mode MODE = Mode.BY_TERM_PLUS_LANG;

	Map<Concept, List<Description>> inactivations = new HashMap<>();

	Map<String, Set<Concept>> descriptionMap;

	protected InactivateDescriptions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
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
		if (!inactivations.containsKey(c) || inactivations.get(c).isEmpty()) {
			throw new TermServerScriptException("Inactivations seem to have gotten lost for " + c);
		}
		
		for (Description dCached : inactivations.get(c)) {
			//This is our local cached copy, we need the object loaded from the TS
			for (Description d : c.getDescriptions()) {
				if (d.getId().equals(dCached.getId())) {
					if (d.isActiveSafely()) {
						removeDescription(t, c, d, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
						changesMade++;
					} else {
						report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Description already inactive: " + d);
					}
				}
			}
		}
		
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		if (MODE == Mode.BY_DESCRIPTION_ID) {
			return identifyConceptById(lineItems);
		} else {
			return identifyConceptByTerm(lineItems);
		}
	}

	private List<Component> identifyConceptById(String[] lineItems) throws TermServerScriptException {
		String descriptionStr = lineItems[0];
		//We're expecting a description.toString() to be passed in, split it back out
		String[] elements = descriptionStr.split(" ");
		String conceptId = elements[1].replace("\\[", "").replace("\\]", "");
		Concept c = gl.getConcept(conceptId);
		//Find that description
		for (Description d : c.getDescriptions()) {
			if (d.getId().equals(elements[0])) {
				if (d.isActiveSafely()) {
					//Have we seen this concept before?
					if (inactivations.containsKey(c)) {
						inactivations.get(c).add(d);
					} else {
						List<Description> inactivationList = new ArrayList<>();
						inactivationList.add(d);
						inactivations.put(c, inactivationList);
					}
				} else {
					LOGGER.warn("Already inactive: {}", d);
					return Collections.emptyList();
				}
				break;
			}
		}
		return Collections.singletonList(c);
	}

	private List<Component> identifyConceptByTerm(String[] lineItems) throws TermServerScriptException {
		if (descriptionMap == null) {
			populateDescriptionMap();
		}
		String term = lineItems[0];
		String lang = lineItems[1];
		Set<Concept> concepts = descriptionMap.get(term);
		if (concepts == null) {
			report(PRIMARY_REPORT, null, null, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "No concept found for " + term + "|" + lang);
			return Collections.emptyList();
		}

		for (Concept c : concepts) {
			List<Description> descriptionsToInactivate = new ArrayList<>();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().equals(term) && d.getLang().equals(lang)) {
					descriptionsToInactivate.add(d);
				}
			}
			if (descriptionsToInactivate.size() > 1) {
				report(PRIMARY_REPORT, c, null, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Multiple descriptions found for " + term + "|" + lang);
			}

			if (!descriptionsToInactivate.isEmpty()) {
				inactivations.put(c, descriptionsToInactivate);
			}

		}

		return Collections.emptyList();
	}

	private void populateDescriptionMap() {
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				Set<Concept> concepts = descriptionMap.computeIfAbsent(d.getTerm(), k -> new HashSet<>());
				concepts.add(c);
			}
		}
	}
}
