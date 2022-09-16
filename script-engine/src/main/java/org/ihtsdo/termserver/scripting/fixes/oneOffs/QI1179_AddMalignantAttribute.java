package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-1179
 */
public class QI1179_AddMalignantAttribute extends BatchFix {
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;
	private RelationshipTemplate workAroundToRemove;
	String inclusionText = "primary";
	
	protected QI1179_AddMalignantAttribute(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		QI1179_AddMalignantAttribute fix = new QI1179_AddMalignantAttribute(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.runStandAlone = true;
			fix.populateTaskDescription = false;
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
		subsetECL = "< 372087000 |Primary malignant neoplasm (disorder)| MINUS  << 269475001 |Malignant tumor of lymphoid, hemopoietic AND/OR related tissue (disorder)|";
		relTemplate = new RelationshipTemplate(PATHOLOGICAL_PROCESS, gl.getConcept("1234914003 |Malignant proliferation of primary neoplasm|"));
		workAroundToRemove = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("86049000 |Malignant neoplasm, primary (morphologic abnormality)|"));
		exclusions = new HashSet<>();
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = addAttribute(task, loadedConcept);
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

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		if (isExcluded(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			List<RelationshipGroup> groupsToProcess = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, FINDING_SITE);
			for (RelationshipGroup g : groupsToProcess) {
				//Is this an additional group that we need to remove?
				if (groupsToProcess.size() > 1 && g.containsTypeValue(workAroundToRemove)) {
					//Does the finding site in this group match the finding site in another group?
					Concept thisFindingSiteValue = g.getValueForType(FINDING_SITE, true);
					RelationshipTemplate findMe = new RelationshipTemplate(FINDING_SITE, thisFindingSiteValue);
					if (SnomedUtils.hasAttributeInAnotherGroup(c, findMe, g.getGroupId())) {
						removeRelationshipGroup(t, c, g);
					}
				}
				Relationship attrib = relTemplate.createRelationship(c, g.getGroupId(), null);
				changesMade += addRelationship(t, c, attrib);
			}
			return changesMade;
		}
		return NO_CHANGES_MADE;
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
		return findConcepts(subsetECL).parallelStream()
				.filter(c -> c.getFsn().toLowerCase().contains(inclusionText))
				.filter(c -> !gl.isOrphanetConcept(c))
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}
}
