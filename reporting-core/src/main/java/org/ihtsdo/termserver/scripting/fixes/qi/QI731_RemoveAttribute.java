package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * QI-731 Remove specified attribute from matching ECL
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QI731_RemoveAttribute extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI731_RemoveAttribute.class);

	String ecl = "<< 125605004 |Fracture of bone (disorder)| : 42752001 |Due to (attribute)| = 773760007 |Traumatic event (event)|";
	RelationshipTemplate removeMe;
	
	protected QI731_RemoveAttribute(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		QI731_RemoveAttribute fix = new QI731_RemoveAttribute(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.populateTaskDescription = false;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		removeMe = new RelationshipTemplate(DUE_TO, gl.getConcept("773760007 |Traumatic event (event)|"));
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		
		for (Relationship r : loadedConcept.getRelationships(removeMe, ActiveState.ACTIVE)) {
			changesMade += removeRelationship(t, loadedConcept, r);
		}
		
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return new ArrayList<>(findConcepts(ecl));
	}

}
