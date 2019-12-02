package org.ihtsdo.termserver.scripting.fixes.qi;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * MQI-5 For a specific list of concepts, switch  |Associated morphology (attribute)| value 
 * from |Cataract (morphologic abnormality)| to 
 * |Abnormally opaque structure (morphologic abnormality)|
 */
public class MQI5_SwitchAssocMorp extends BatchFix {
	
	RelationshipTemplate find;
	RelationshipTemplate replace;
	
	protected MQI5_SwitchAssocMorp(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MQI5_SwitchAssocMorp fix = new MQI5_SwitchAssocMorp(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.reportNoChange = true;
			fix.expectNullConcepts = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		find = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("128306009 |Cataract (morphologic abnormality)|"));
		replace = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("128305008 |Abnormally opaque structure (morphologic abnormality)|"));
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
		//Switch all stated relationships from the findValue to the replaceValue
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, find).size() > 0) {
			changesMade += replaceRelationship(t, c, find, replace);
		} else {
			throw new ValidationFailure(c, "Concept did not contain expected relationship");
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, find).size() == 0) {
			report ((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept did not contain expected stated relationship");
			return null;
		}
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}

}
