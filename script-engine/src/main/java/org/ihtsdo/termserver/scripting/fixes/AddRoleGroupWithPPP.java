package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-9382 Add Interprets/Has Interpretation pair and also set PPP if required
 */
public class AddRoleGroupWithPPP extends BatchFix {
	
	private Set<String> exclusions;
	private Concept ppp = null;
	private List<RelationshipGroupTemplate> groupsToAdd = new ArrayList<>();
	
	protected AddRoleGroupWithPPP(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		AddRoleGroupWithPPP fix = new AddRoleGroupWithPPP(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//INFRA-9382
		ppp = gl.getConcept("110359009 |Intellectual disability (disorder)|");
		subsetECL = "<< 110359009 |Intellectual disability (disorder)| : * =  308490002 |Pathological developmental process (qualifier value)| ";
		
		RelationshipGroupTemplate groupToAdd1 = new RelationshipGroupTemplate();
		groupToAdd1.addRelationshipTemplate(gl.getConcept("363714003|Interprets|"), gl.getConcept("247573007|Intellectual Ability|"));
		groupToAdd1.addRelationshipTemplate(gl.getConcept("363713009|Has Interpretation|"), gl.getConcept("260379002|Impaired|"));
		groupsToAdd.add(groupToAdd1);
		
		RelationshipGroupTemplate groupToAdd2 = new RelationshipGroupTemplate();
		groupToAdd2.addRelationshipTemplate(gl.getConcept("363714003|Interprets|"), gl.getConcept("406208005|Adaption Behaviour|"));
		groupToAdd2.addRelationshipTemplate(gl.getConcept("363713009|Has Interpretation|"), gl.getConcept("260379002|Impaired|"));
		groupsToAdd.add(groupToAdd2);
		
		exclusions = new HashSet<>();
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			if (ppp != null) {
				changesMade += checkAndSetProximalPrimitiveParent(task, loadedConcept, ppp, false, false);
			}
			
			changesMade += addRoleGroups(task, loadedConcept);
			if (changesMade > 0) {
				String expression = loadedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				updateConcept(task, loadedConcept, info);
				report (task, loadedConcept, Severity.NONE, ReportActionType.INFO, expression);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int addRoleGroups(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		if (isExcluded(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			for (RelationshipGroupTemplate groupToAdd : groupsToAdd) {
				int nextFreeGroup = SnomedUtils.getFirstFreeGroup(c);
				changesMade += addRelationshipGroup(t, c, groupToAdd, nextFreeGroup, false); //For interprets/has interpretation we'll have more than one of those.
			}
		}
		return changesMade;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		for (String exclusionWord : exclusions) {
			if (fsn.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return new ArrayList<>(findConcepts(subsetECL));
	}
}
