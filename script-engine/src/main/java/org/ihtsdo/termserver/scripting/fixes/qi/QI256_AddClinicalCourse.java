package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-256
 * For all < 429040005 |Ulcer (disorder)|
 * 1. 'Chronic' in FSN description 
 *  Flag up if Associated morphology (attribute)is not << 405719001 |Chronic ulcer (morphologic abnormality)|
 *  and Add Group zero: 263502005 |Clinical course (attribute)| = 90734009 |Chronic (qualifier value)|
 *
 * 2. 'Acute' in FSN description 
 * Flag up if Associated morphology (attribute)is not << 26317001 |Acute ulcer (morphologic abnormality)|
 * Add Group zero: 263502005 |Clinical course (attribute)| = 424124008 |Sudden onset AND/OR short duration (qualifier value)|
 */
public class QI256_AddClinicalCourse extends BatchFix {
	Concept clinicalCourse;
	Concept chronicUlcer;
	Concept acuteUlcer;
	AncestorsCache aCache;
	
	protected QI256_AddClinicalCourse(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		QI256_AddClinicalCourse fix = new QI256_AddClinicalCourse(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
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
		aCache = gl.getAncestorsCache();
		clinicalCourse = gl.getConcept("263502005 |Clinical course (attribute)|");
		acuteUlcer = gl.getConcept("26317001 |Acute ulcer (morphologic abnormality)|");
		chronicUlcer = gl.getConcept("405719001 |Chronic ulcer (morphologic abnormality)|");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = addClinicalCourse(task, loadedConcept);
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

	private int addClinicalCourse(Task t, Concept c) throws TermServerScriptException {
		String fsn = c.getFsn().toLowerCase();
		boolean isChronic = fsn.contains("chronic");
		boolean isAcute = fsn.contains("acute");
		checkMorphology(t, c, isChronic);
		if ((!isChronic && !isAcute) || (isChronic && isAcute)) {
			throw new ValidationFailure(t, c, "Logic failure in chronic/acute detection");
		}
		Concept target = isChronic ? gl.getConcept("90734009|Chronic|") : gl.getConcept("424124008 |Sudden onset AND/OR short duration|");
		Relationship r = new Relationship(c, clinicalCourse, target, UNGROUPED);
		return addRelationship(t, c, r);
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		//Find all descendants of < 429040005 |Ulcer (disorder)|
		for (Concept c : gl.getConcept(429040005L).getDescendants(NOT_SET)) {
			String fsn = c.getFsn().toLowerCase();
			boolean isChronic = fsn.contains("chronic");
			boolean isAcute = fsn.contains("acute");
			if (isChronic || isAcute) {
				if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, clinicalCourse, UNGROUPED).size() == 0) {
					processMe.add(c);
				} else {
					checkMorphology(null, c, isChronic);
				}
			} 
		}
		return processMe;
	}

	private void checkMorphology(Task t, Concept c, boolean isChronic) throws TermServerScriptException {
		//Are we missing a morphology to align with the FSN?
		Concept morphValue = isChronic ? gl.getConcept("405719001 |Chronic ulcer|") : gl.getConcept("26317001 |Acute ulcer|");
		if (SnomedUtils.getSubsumedRelationships(c, ASSOC_MORPH, morphValue, CharacteristicType.STATED_RELATIONSHIP, aCache).size() == 0) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Associated morphology is not << " + morphValue);
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		throw new NotImplementedException();
	}

}
