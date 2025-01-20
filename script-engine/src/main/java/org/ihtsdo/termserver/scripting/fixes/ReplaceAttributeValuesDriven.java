package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * LOINC-387 A batch update is required for concepts/expressions on this tab as following:
 * - Change property from 118586006 |Ratio (property) (qualifier value)| to 784316008 |Arbitrary fraction (property) (qualifier value)|
 * - Add Attribute value 704325000 |Relative to (attribute)| 48583005 |Immunoglobulin E (substance)|
 */
public class ReplaceAttributeValuesDriven extends BatchFix {
	private Concept targetType;
	private Map<Concept, Concept> replacementValuesMap;
	private RelationshipTemplate addAttribute;
	
	protected ReplaceAttributeValuesDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceAttributeValuesDriven fix = new ReplaceAttributeValuesDriven(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = false;
			fix.correctRoundedSCTIDs = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		replacementValuesMap = new HashMap<>();
		/*replacementValuesMap.put(gl.getConcept("118586006 |Ratio (property) (qualifier value)| "), 
				gl.getConcept("784316008 |Arbitrary fraction (property) (qualifier value)|"));
		addAttribute = new RelationshipTemplate(gl.getConcept("704325000 |Relative to (attribute)| "),
				gl.getConcept("48583005 |Immunoglobulin E (substance)|"));*/
		targetType = gl.getConcept("424226004 |Using device (attribute)|");
		replacementValuesMap.put(gl.getConcept("79068005 |Needle, device|"), gl.getConcept("706681000 |Aspiration needle|"));
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
		for (Concept targetTarget : replacementValuesMap.keySet()) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(targetType) && r.getTarget().equals(targetTarget)) {
					Relationship replacement = r.clone();
					replacement.setTarget(replacementValuesMap.get(targetTarget));
					changesMade += replaceRelationship(t, c, r, replacement);
				}
			}
		}
		
		if (addAttribute != null) {
			changesMade += addRelationship(t, c, addAttribute, SnomedUtils.getFirstFreeGroup(c));
		}
		
		return changesMade;
	}
}
