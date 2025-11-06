package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * QI-28 For << 95896000 |Protozoan infection (disorder)|
 * Switch any 370135005 |Pathological process (attribute)| values
 * from 441862004 |Infectious process (qualifier value)|
 * to 442614005 |Parasitic process (qualifier value)|
 * BUT only where we have a causative agent which is  <<  417396000 |Kingdom Protozoa (organism)|
 * 
 * QI-310 Batch change request for <<81060008 |Intestinal obstruction (disorder)| with the relationship group
 * { 363698007 |Finding site (attribute)| = 113276009 |Intestinal structure (body structure)|, 116676008 |Associated morphology (attribute)| = 26036001 |Obstruction (morphologic abnormality)| }
 * The value for finding site needs to change to 783798004 |Structure of lumen of intestine (body structure)|
 */
public class SwitchAttributeInGroup extends BatchFix {

	String subHierarchy = "81060008"; // |Intestinal obstruction (disorder)| 
	RelationshipTemplate findRel;
	RelationshipTemplate replaceRel;
	RelationshipTemplate alsoPresentRel;

	protected SwitchAttributeInGroup(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SwitchAttributeInGroup fix = new SwitchAttributeInGroup(null);
		try {
			fix.populateEditPanel = false;
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
		Concept findValue = gl.getConcept("113276009"); // |Intestinal structure (body structure)|
		findRel = new RelationshipTemplate(FINDING_SITE, findValue);
		
		Concept replaceValue = gl.getConcept("783798004"); // |Structure of lumen of intestine (body structure)|
		replaceRel = new RelationshipTemplate(FINDING_SITE, replaceValue);
		
		Concept value = gl.getConcept("26036001"); // |Obstruction (morphologic abnormality)|
		alsoPresentRel = new RelationshipTemplate(ASSOC_MORPH, value);
		/*alsoPresentType = gl.getConcept("246075003"); // |Causative agent (attribute)|
		Concept alsoPresent = gl.getConcept(alsoPresentHierarchy); 
		alsoPresentValues = alsoPresent.getDescendants(NOT_SET);*/
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = switchValues(t, loadedConcept, false);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept c, boolean quiet) throws TermServerScriptException {
		int changesMade = 0;
		//Switch all stated relationships from the findValue to the replaceValue
		for (Relationship r : c.getRelationships(findRel, ActiveState.ACTIVE)) {
			//Do we also have the "also present" rel in the same group?
			if (c.getRelationship(alsoPresentRel, r.getGroupId()) != null) {
				if (quiet) {
					changesMade++;
				} else {
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
		for (Concept c : gl.getConcept(subHierarchy).getDescendants(NOT_SET)) {
			//Try making the change and see if changes are made
			if (switchValues(null, c.clone(null), true) > 0) {
				processMe.add(c);
			}
		}
		return processMe;
	}
}
