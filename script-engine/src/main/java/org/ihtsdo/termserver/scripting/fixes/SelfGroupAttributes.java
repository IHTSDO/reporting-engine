package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
INFRA-8024 Move an attribute out of a group to be self grouped
*/
public class SelfGroupAttributes extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(SelfGroupAttributes.class);
	public static final String ecl = "<< 437481000124109 |Amino acid modified diet (regime/therapy)| OR << 435581000124102 |Carbohydrate modified diet (regime/therapy)| OR << 435671000124101 |Cholesterol modified diet (regime/therapy)| OR << 1055204001 |Fat and oil modified diet (regime/therapy)| OR << 1156315004 |Plant fiber modified diet (regime/therapy)| OR << 435781000124109 |Mineral modified diet (regime/therapy)| OR << 1052337007 |Protein and protein derivative modified diet (regime/therapy)| OR << 435811000124106 |Vitamin modified diet (regime/therapy)| OR << 435701000124100 |Energy modified diet (regime/therapy)|";
	
	Concept targetAttributeType;
	
	protected SelfGroupAttributes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SelfGroupAttributes fix = new SelfGroupAttributes(null);
		try {
			fix.additionalReportColumns="Before, After";
			fix.selfDetermining = true;
			fix.reportNoChange = false;
			fix.populateTaskDescription = false;  //Going above some limit
			fix.populateEditPanel = false; //Going above some limit
			fix.validateConceptOnUpdate = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"); //Ad-Hoc Batch Updates
		//Populate our attributes of interest
		targetAttributeType = gl.getConcept("363702006 |Has focus (attribute)|");  
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = selfGroupAttribute(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int selfGroupAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, targetAttributeType, ActiveState.ACTIVE)) {
			//Are there other attributes in this group?  
			if (c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, r.getGroupId()).size() > 0) {
				String before = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				removeRelationship(t, c, r);
				Relationship selfGrouped = r.clone();
				selfGrouped.setActive(true);
				selfGrouped.setGroupId(SnomedUtils.getFirstFreeGroup(c));
				c.addRelationship(selfGrouped);
				String after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, before, after);
				changesMade ++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Collection<Concept> allPotential = SnomedUtils.sort(findConcepts(ecl));
		List<Concept> allAffected = new ArrayList<>();
		
		nextConcept:
		for (Concept c : allPotential) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, targetAttributeType, ActiveState.ACTIVE)) {
				//Are there other attributes in this group?  
				if (c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, r.getGroupId()).size() > 0) {
					allAffected.add(c);
					continue nextConcept;
				}
			}
		}
		LOGGER.info("Detected " + allAffected.size() + " concepts to modify");
		return new ArrayList<Component>(allAffected);
	}

}
