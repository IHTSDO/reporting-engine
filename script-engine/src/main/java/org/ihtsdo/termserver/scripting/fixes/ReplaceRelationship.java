package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
For APDS-335, QI-234 (QI-99)
Replace all relationships of one specified type / value for another
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceRelationship extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceRelationship.class);

	Concept findAttribute;
	Concept replaceAttribute;
	
	enum Mode { TYPE, VALUE }
	Mode mode = Mode.VALUE;
	
	protected ReplaceRelationship(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceRelationship fix = new ReplaceRelationship(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = false;
			fix.populateTaskDescription = false;  //Going above some limit
			fix.populateEditPanel = false; //Going above some limit
			fix.validateConceptOnUpdate = false;
			LOGGER.warn("Description and Edit panel not being populated due to task size");
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
		findAttribute = gl.getConcept("23583003 |Inflammation (morphologic abnormality)|");  
		replaceAttribute = gl.getConcept("409774005 |Inflammatory morphology (morphologic abnormality)|"); 
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = replaceTargetRelationship(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int replaceTargetRelationship(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			Concept match = mode.equals(Mode.TYPE) ? r.getType() : r.getTarget();
			if (match.equals(findAttribute)) {
				//Clone r and modify
				Relationship replacement = r.clone(null);
				switch (mode) {
				case TYPE : replacement.setType(replaceAttribute);
						break;
				case VALUE : 
					replacement.setTarget(replaceAttribute);
				}
				
				replaceRelationship(t, c, r, replacement);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Collection<Concept> allPotential = gl.getAllConcepts();
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : allPotential) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				Concept match = mode.equals(Mode.TYPE) ? r.getType() : r.getTarget();
				if (match.equals(findAttribute)) {
					allAffected.add(c);
					break;
				}
			}
		}
		LOGGER.info("Detected " + allAffected.size() + " concepts to modify");
		return new ArrayList<Component>(allAffected);
	}

}
