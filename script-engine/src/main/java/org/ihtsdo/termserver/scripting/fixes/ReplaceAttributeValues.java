package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-7531 Replace 257867005 |Insertion - action (qualifier value)| with 129336009 |Implantation - action (qualifier value)|
 * QI-1143 Replace 86049000 |Malignant neoplasm, primary| with 1240414004 |Malignant neoplasm morphology|
 */
public class ReplaceAttributeValues extends BatchFix {
	
	Map<Concept, Concept> replacementMap;
	String ecl = "< 363787002 |Observable entity| : * = 86049000 |Malignant neoplasm, primary (morphologic abnormality)|";
	RelationshipTemplate addRelationship; 
	
	protected ReplaceAttributeValues(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReplaceAttributeValues fix = new ReplaceAttributeValues(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
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
		replacementMap = new HashMap<>();
		replacementMap.put(gl.getConcept("86049000 |Malignant neoplasm, primary|"), 
							gl.getConcept("1240414004 |Malignant neoplasm morphology|"));
		
		Concept addType = gl.getConcept(" 704321009 |Characterizes (attribute)|");
		Concept addTarget = gl.getConcept("1234914003 |Malignant proliferation of primary neoplasm (qualifier value)|");
		addRelationship = new RelationshipTemplate(addType, addTarget, RelationshipTemplate.Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS);
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = switchValues(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (Concept targetTarget : replacementMap.keySet()) {
			//Switch all stated relationships from the findValue to the replaceValue
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getTarget().equals(targetTarget)) {
					Relationship replacement = r.clone();
					replacement.setTarget(replacementMap.get(targetTarget));
					changesMade += replaceRelationship(t, c, r, replacement);
				}
			}
		}
		
		if (addRelationship != null) {
			int groupId = SnomedUtils.getFirstFreeGroup(c);
			Relationship r = addRelationship.createRelationship(c, groupId, null);
			addRelationship(t, c, r, addRelationship.getMode());
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> allAffected = new ArrayList<>();
		info("Identifying concepts to process");
		
		List<Concept> concepts = SnomedUtils.sort(findConcepts(ecl));
		
		nextConcept:
		//for (Concept c : gl.getAllConcepts()) {
		for (Concept c : concepts) {
			for (Concept targetTarget : replacementMap.keySet()) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (!r.isConcrete() && r.getTarget().equals(targetTarget)) {
						allAffected.add(c);
						continue nextConcept;
					}
				}
			}
		}
		info ("Identified " + allAffected.size() + " concepts to process");
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}
}
