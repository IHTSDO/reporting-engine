package org.ihtsdo.termserver.scripting.fixes;

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
 * QI-1208 Replace 6920004 |Defect (morphologic abnormality)| with 783804002 |Abnormal communication (morphologic abnormality)|
 * QI-1221 For Direct Morphologies, replace 6920004 |Defect (morphologic abnormality)| with 783804002 |Abnormal communication (morphologic abnormality)|
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceAttributeValues extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceAttributeValues.class);

	Map<Concept, Concept> replacementMap;
	Concept restrictToType = null;
	//String ecl = "< 363787002 |Observable entity| : * = 86049000 |Malignant neoplasm, primary (morphologic abnormality)|";
	//String ecl = "* : * = 367651003  |Malignant neoplasm of primary, secondary, or uncertain origin (morphologic abnormality)|";
	//String ecl = "<< 253273004 |Cardiac septal defects (disorder)| OR << 768552007 |Congenital ventricular septal defect (disorder)| ";
	//String ecl = "<<53941002 |Closure of septal fenestration of heart (procedure)|";
	//String ecl = "<< 61557004 |Implantation of joint prosthesis (procedure)|";
	//String ecl = "<< 1288025000 |Prosthetic arthroplasty of joint (procedure)|";
	String ecl = "<< 1288025000 |Prosthetic arthroplasty of joint (procedure)| OR << 61557004 |Implantation of joint prosthesis (procedure)| ";
	RelationshipTemplate addRelationship;
	
	protected ReplaceAttributeValues(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceAttributeValues fix = new ReplaceAttributeValues(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			//fix.runStandAlone = true;
			fix.taskPrefix = "Observable ";
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
		
		Concept find = gl.getConcept("425362007 |Surgical insertion - action (qualifier value)|");
		Concept replace = gl.getConcept("129338005 |Surgical implantation - action (qualifier value)|");

		replacementMap.put(find, replace);
		//replacementMap.put(gl.getConcept("371520008 |Developmental failure of fusion (morphologic abnormality)|"), replacement); 		
		
		//restrictToType = gl.getConcept("363700003 |Direct morphology (attribute)|");
		restrictToType = METHOD;
				
		//replacementMap.put(gl.getConcept("367651003 |Malignant neoplasm of primary, secondary, or uncertain origin (morphologic abnormality)|"), 
		//					gl.getConcept("1240414004 |Malignant neoplasm|"));
		
		//Concept addType = gl.getConcept(" 704321009 |Characterizes (attribute)|");
		//Concept addTarget = gl.getConcept("1234914003 |Malignant proliferation of primary neoplasm (qualifier value)|");
		//addRelationship = new RelationshipTemplate(addType, addTarget, RelationshipTemplate.Mode.UNIQUE_TYPE_VALUE_ACROSS_ALL_GROUPS);
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = switchValues(task, loadedConcept);
			if (changesMade > 0) {
				//changesMade += checkAndSetProximalPrimitiveParent(task, loadedConcept, null);
			}
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
					if (restrictToType != null && !r.getType().equals(restrictToType)) {
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Expected value found, but not in expected attribute type", r);
						continue;
					}
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
		LOGGER.info("Identifying concepts to process");
		
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
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}
}
