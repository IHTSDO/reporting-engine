package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * NUTRITION-58 Inactivate attributes matching a particular patern
 */
public class InactivateAttributes extends BatchFix {

	private RelationshipTemplate inactivateTemplate;
	
	protected InactivateAttributes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateAttributes fix = new InactivateAttributes(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
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
		subsetECL = "<< 364393001 |Nutritional observable (observable entity)| :  370132008 |Scale type (attribute)| = 30766002 |Quantitative (qualifier value)| ";
		inactivateTemplate = new RelationshipTemplate(gl.getConcept("370132008 |Scale type (attribute)|"), 
				gl.getConcept("30766002 |Quantitative (qualifier value)|"));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade += inactivateAttribute(task, loadedConcept);
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

	private int inactivateAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(inactivateTemplate, ActiveState.ACTIVE)) {
			changesMade += removeRelationship(t, c, r);
		}
		return changesMade;
	}
}
