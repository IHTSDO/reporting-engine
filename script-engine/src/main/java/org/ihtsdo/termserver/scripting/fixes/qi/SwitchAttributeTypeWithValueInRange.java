package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * SCTQA-309 Perform a bulk update of physical object concepts that currently have a relationship 
 * of 118170007 |Specimen source identity (attribute)| with a value of 
 * << 260787004 |Physical object (physical object)|. The relationship should be updated so the attribute 
 * is changed from 118170007 |Specimen source identity (attribute)| to 370133003 |Specimen substance (attribute)| 
 * with the value remaining the same
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchAttributeTypeWithValueInRange extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(SwitchAttributeTypeWithValueInRange.class);

	String targetECL = "<< 123038009 |Specimen (specimen)| : 118170007 |Specimen source identity (attribute)| = << 260787004 |Physical object (physical object)| ";
	Concept findType;
	Concept replaceType;
	Collection<Concept> findValueRange;
	
	protected SwitchAttributeTypeWithValueInRange(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SwitchAttributeTypeWithValueInRange fix = new SwitchAttributeTypeWithValueInRange(null);
		try {
			fix.populateEditPanel = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		findType = gl.getConcept("118170007 |Specimen source identity (attribute)|");
		replaceType = gl.getConcept("370133003 |Specimen substance (attribute)|");
		findValueRange = findConcepts("<< 260787004 |Physical object (physical object)|");
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = switchType(t, loadedConcept, false);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}
	
	private int switchType(Task t, Concept c, boolean quiet) throws TermServerScriptException {
		int changesMade = 0;
		//Switch all stated relationships from the findType to the replaceType
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, findType, ActiveState.ACTIVE)) {
			//Where the value is also in the expected range
			if (findValueRange.contains(r.getTarget())) {
				if (quiet) {
					changesMade++;
				} else {
					Relationship replaceRel = r.clone(null);
					replaceRel.setType(replaceType);
					changesMade += replaceRelationship(t, c, r, replaceRel);
				}
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		//Find all descendants of subHierarchy
		for (Concept c : findConcepts(targetECL)) {
			//Try making the change and see if changes are made
			if (switchType(null, c.clone(null), true) > 0) {
				processMe.add(c);
			} else {
				report((Task)null, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept in expected subset did not update", c.toExpression(CharacteristicType.STATED_RELATIONSHIP), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			}
		}
		return processMe;
	}
}
