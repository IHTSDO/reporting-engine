package org.ihtsdo.termserver.scripting.fixes.qi;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * NUTRITION-56 Batch change of Modified substance diet 
 */
public class NUTRITION56_ReplaceAttribute extends BatchFix {

	//private RelationshipTemplate addTemplate;
	private RelationshipTemplate matchTemplate;
	private RelationshipTemplate replaceTemplate;
	
	protected NUTRITION56_ReplaceAttribute(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		NUTRITION56_ReplaceAttribute fix = new NUTRITION56_ReplaceAttribute(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
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
		subsetECL = "<< 182922004 |Dietary regime (regime/therapy)| : 424361007 |Using substance (attribute)| = *";
		matchTemplate = new RelationshipTemplate(gl.getConcept("424361007 |Using substance (attribute)|"), null);
		replaceTemplate = new RelationshipTemplate(gl.getConcept("260686004 |Method (attribute)| "), 
				gl.getConcept("129445006 |Administration - action (qualifier value)|"));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade += addAttribute(task, loadedConcept);
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

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//Find the groupId of the matching relationship template
		
		for (Relationship r : c.getRelationships(matchTemplate, ActiveState.ACTIVE)) {
			if (replaceTemplate != null) {
				Relationship replaceAttrib = replaceTemplate.createRelationship(c, r.getGroupId(), null);
				changesMade += replaceRelationship(t, c, r, replaceAttrib);
			} 
		}
		//If the match is not found, we will add in the new attribute anyway, if it does not exist.
		if (changesMade == NO_CHANGES_MADE) {
			changesMade += addRelationship(t, c, replaceTemplate, SELFGROUPED);
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return new ArrayList<>(findConcepts(subsetECL));
	}
}
