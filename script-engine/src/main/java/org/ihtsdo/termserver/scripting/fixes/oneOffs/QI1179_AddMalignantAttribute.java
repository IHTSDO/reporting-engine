package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate.Mode;
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
	private Map<Concept, Concept> replaceValuesMap;
	
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
		//subsetECL = "< 372087000 |Primary malignant neoplasm (disorder)| MINUS ( << 269475001 |Malignant tumor of lymphoid, hemopoietic AND/OR related tissue (disorder)| OR (< 372087000 |Primary malignant neoplasm (disorder)| : 370135005 |Pathological process (attribute)| = 1234914003 |Malignant proliferation of primary neoplasm (qualifier value)|))";
		subsetECL = "< 363346000 |Malignant neoplastic disease (disorder)| MINUS << 269475001 |Malignant tumor of lymphoid, hemopoietic AND/OR related tissue (disorder)| ";
		relTemplate = new RelationshipTemplate(PATHOLOGICAL_PROCESS, gl.getConcept("1234914003 |Malignant proliferation of primary neoplasm|"));
		workAroundToRemove = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("86049000 |Malignant neoplasm, primary (morphologic abnormality)|"));
		exclusions = new HashSet<>();
		exclusions.add("metastasis");
		replaceValuesMap = new HashMap<>();
		replaceValuesMap.put(gl.getConcept("86049000 |Malignant neoplasm, primary|"), gl.getConcept("1240414004 |Malignant neoplasm morphology|"));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			if (concept.getId().equals("285645000")) {
			//	debug("here");
			}
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
		boolean groupRemoved = false;
		if (isExcluded(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			List<RelationshipGroup> groupsToProcess = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ASSOC_MORPH);
			for (RelationshipGroup g : groupsToProcess) {
				/*boolean thisGroupRemoved = false;
				//Is this an additional group that we need to remove?
				if (groupsToProcess.size() > 1 && g.containsTypeValue(workAroundToRemove)) {
					//Does the finding site in this group match the finding site in another group?
					//Or also remove if self grouped
					Concept thisFindingSiteValue = g.getValueForType(FINDING_SITE, true);
					RelationshipTemplate findMe = new RelationshipTemplate(FINDING_SITE, thisFindingSiteValue);
					if (g.size() == 1 || SnomedUtils.hasAttributeInAnotherGroup(c, findMe, g.getGroupId())) {
						changesMade += removeRelationshipGroup(t, c, g);
						groupRemoved = true;
						thisGroupRemoved = true;
					}
				}
				
				if (!thisGroupRemoved) {*/
					Relationship attrib = relTemplate.createRelationship(c, g.getGroupId(), null);
					changesMade += addRelationship(t, c, attrib, Mode.UNIQUE_TYPE_IN_THIS_GROUP);
				//}
			}
			
			//Self grouped would not be picked up in above due to lack of finding site
			//We can also add in the pathological process here
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ASSOC_MORPH)) {
				if (g.size() == 1) {
				if (g.containsTypeValue(workAroundToRemove)) {
						changesMade +=  removeRelationshipGroup(t, c, g);
						groupRemoved = true;
					} else {
						Relationship attrib = relTemplate.createRelationship(c, g.getGroupId(), null);
						changesMade += addRelationship(t, c, attrib, Mode.UNIQUE_TYPE_IN_THIS_GROUP);
					}
				}
			}
			
			if (groupRemoved) {
				shuffleDown(t, c);
			}
			
			//Now swap out all our attribute values that we're replacing - if they're still here
			for (Concept targetTarget : replaceValuesMap.keySet()) {
				//Switch all stated relationships from the findValue to the replaceValue
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getTarget().equals(targetTarget)) {
						Relationship replacement = r.clone();
						replacement.setTarget(replaceValuesMap.get(targetTarget));
						changesMade += replaceRelationship(t, c, r, replacement);
					}
				}
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
				.filter(c -> !isExcluded(c))
				//.filter(c -> !gl.isOrphanetConcept(c))
				.filter(c -> c.getRelationships(relTemplate, ActiveState.ACTIVE).size() == 0)
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}
}
