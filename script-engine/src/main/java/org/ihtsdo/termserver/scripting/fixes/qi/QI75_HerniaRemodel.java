package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI-75
 * For the subtypes of 8377001 |Hernia, with obstruction (disorder)| and 79990007 |Hernia, with gangrene (disorder)| I want to do the following:
 * Addition of 116680003 |Is a (attribute)|=116223007 |Complication (disorder)|
 * Inactivate 116680003 |Is a (attribute)|=8377001 |Hernia, with obstruction (disorder)| and/or 79990007 |Hernia, with gangrene (disorder)| if present. Note, these may have been added this release cycle so we need to take into account the born inactive relationship issue. I will be inactivating |Hernia, with obstruction (disorder)| and 79990007 |Hernia, with gangrene (disorder)| when work is done.
 * Addition of 42752001 |Due to (attribute)|= This needs to be a hernia concept but which one changes depending on what the hernia concept is e.g.196835005 |Obstruction co-occurrent and due to femoral hernia (disorder)| needs to be due to the femoral hernia. If the hernia was recurrent it would be a recurrent <x> hernia. If it's not feasible to do in a batch then I'll do manually.
 * Can we do in batches of 10? Or can we do this in batches per hernia type perhaps- would that help with the due to issue?
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QI75_HerniaRemodel extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI75_HerniaRemodel.class);

	Concept hWithO;
	Concept hWithG;
	Relationship complicationParent;
	Relationship hWithOParent;
	Relationship hWithGParent;
	
	Map<String, Concept> typesOfHernia;
	
	protected QI75_HerniaRemodel(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		QI75_HerniaRemodel fix = new QI75_HerniaRemodel(null);
		try {
			fix.populateEditPanel = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.classifyTasks = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		hWithO = gl.getConcept("8377001 |Hernia, with obstruction (disorder)|");
		hWithG = gl.getConcept("79990007 |Hernia, with gangrene (disorder)|");
		complicationParent = new Relationship(IS_A, COMPLICATION);
		hWithOParent = new Relationship(IS_A, hWithO);
		hWithGParent = new Relationship(IS_A, hWithG);
		
		//Populate all known types of hernia
		Concept hernia = gl.getConcept("52515009 |Hernia of abdominal cavity (disorder)|");
		typesOfHernia = new HashMap<>();
		for (Concept c : hernia.getDescendants(NOT_SET)) {
			String term = SnomedUtils.deconstructFSN(c.getFsn())[0].toLowerCase();
			typesOfHernia.put(term, c);
		}
		LOGGER.info("Mapped " + typesOfHernia.size() + " types of hernia");
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = remodelHernia(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int remodelHernia(Task t, Concept c) throws TermServerScriptException {
		Relationship complicationParentRel = complicationParent.clone(null);
		complicationParentRel.setSource(c);
		int changesMade = addRelationship(t, c, complicationParentRel);
		changesMade += removeParentRelationship(t, hWithOParent, c, "Complication", null);
		changesMade += removeParentRelationship(t, hWithGParent, c, "Complication", null);
		changesMade += addDueToTarget(t, c);
		return changesMade;
	}

	private int addDueToTarget(Task t, Concept c) throws TermServerScriptException {
		String msg = "";
		String term = SnomedUtils.deconstructFSN(c.getFsn())[0];
		//Can we split on "due to" ?
		String[] parts = term.split("due to");
		if (parts.length == 1) {
			msg = "Can't work out due to for " + c;
		} else {
			String searchTerm = parts[1].trim().toLowerCase();
			Concept target = typesOfHernia.get(searchTerm);
			if (target == null) {
				msg = "Did not find heria called '" + searchTerm + "'";
			} else {
				Relationship r = new Relationship(c, DUE_TO, target, UNGROUPED);
				return addRelationship(t, c, r);
			}
		}
		report(t,c,Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
		return NO_CHANGES_MADE;
	}

	@Override
	/* ECL to find candidates:
	 *  << 404684003 : 42752001 = << 404684003
	 */
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Component> processMe = new HashSet<>();
		processMe.addAll(gl.getDescendantsCache().getDescendantsOrSelf(hWithO));
		processMe.addAll(gl.getDescendantsCache().getDescendantsOrSelf(hWithG));
		return new ArrayList<Component>(processMe);
	}
	
	protected Batch formIntoBatch (List<Component> allComponents) throws TermServerScriptException {
		//Form into 3 lists - 8377001 |Hernia, with obstruction (disorder)|
		// 79990007 |Hernia, with gangrene (disorder)| and both
		List<Component> groupOne = new ArrayList<>();
		List<Component> groupTwo = new ArrayList<>();
		List<Component> groupThree = new ArrayList<>();
		List<List<Component>> buckets = new ArrayList<>();
		buckets.add(groupOne);
		buckets.add(groupTwo);
		buckets.add(groupThree);
		
		//Sort into one of three buckets
		for (Component comp : allComponents) {
			Concept c = (Concept)comp;
			boolean isObstruction = gl.getDescendantsCache().getDescendantsOrSelf(hWithO).contains(c);
			boolean isGangrene = gl.getDescendantsCache().getDescendantsOrSelf(hWithG).contains(c);
			if (isObstruction) {
				//Is it both?
				if (isGangrene) {
					groupThree.add(c);
				} else {
					groupOne.add(c);
				}
			} else if (isGangrene) {
				//Update - exclude 'just' Gangrene from this work
				report((Task)null, c, Severity.NONE, ReportActionType.NO_CHANGE, "Skipping hernia with 'just' gangrene");
			} else {
				throw new TermServerScriptException("Concept outside of expected categories: " + c);
			}
		}
		return formIntoGroupedBatch(buckets);
	}

}
