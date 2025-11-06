package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
SUBST-267 Remove stated relationships of a given attribute type where the classifier is 
already removing them.
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveRedundantRelationships extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveRedundantRelationships.class);

	Concept subHierarchy = SUBSTANCE;
	Concept attributeType = HAS_DISPOSITION;
	
	protected RemoveRedundantRelationships(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RemoveRedundantRelationships fix = new RemoveRedundantRelationships(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeRedundantRelationships(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, info);
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int removeRedundantRelationships(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
			//How many of these do we have?
			if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).isEmpty()) {
				removeRelationship(t, c, r);
				changesMade++;
			} else {
				report(t, c, Severity.NONE, ReportActionType.INFO, "Retained: " + r);
			}
		}
		return changesMade;
	}


	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return Collections.singletonList(new Concept(lineItems[0]));
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		LOGGER.info("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		Collection<Concept> subHierarchy = gl.getConcept(this.subHierarchy.getConceptId()).getDescendants(NOT_SET);
		for (Concept c : subHierarchy) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				//How many of these do we have?
				if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).isEmpty()) {
					processMe.add(c);
					break;
				}
			}
		}
		addSummaryInformation("Concepts checked", subHierarchy.size());
		processMe.sort(Comparator.comparing(Concept::getFsn));
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		return asComponents(processMe);
	}

}
