package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-1206 Remove attributes using 105590001 |Substance (substance)|
 * INFRA-115951 Bulk Change: Remove therapeutic intent from Therapy (regime/therapy)
 */
public class RemoveAttributes extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveAttributes.class);

	Set<RelationshipTemplate> removeAttributes;
	String subsetECL = "<<276239002 |Therapy (regime/therapy)|";

	protected RemoveAttributes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RemoveAttributes fix = new RemoveAttributes(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		removeAttributes = new HashSet<>();
		removeAttributes.add(new RelationshipTemplate(
				gl.getConcept("363703001 |Has intent|"),
				gl.getConcept("262202000 |Therapeutic intent|")));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = removeAttributes(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int removeAttributes(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (RelationshipTemplate removeMe : removeAttributes) {
			for (Relationship r : c.getRelationships(removeMe, ActiveState.ACTIVE)) {
				changesMade += removeRelationship(t, c, r);
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> allAffected = new ArrayList<>();
		LOGGER.info("Identifying concepts to process...");
		
		nextConcept:
		//for (Concept c : gl.getAllConcepts()) {
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL))) {
			for (RelationshipTemplate rt : removeAttributes) {
				if (c.getRelationships(rt, ActiveState.ACTIVE).size() > 0) {
					allAffected.add(c);
					continue nextConcept;
				}
			}
		}
		LOGGER.info("Identified {} concepts to process", allAffected.size());
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}
}
