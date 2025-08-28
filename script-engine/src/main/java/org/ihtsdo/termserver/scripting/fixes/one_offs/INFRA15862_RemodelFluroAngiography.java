package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class INFRA15862_RemodelFluroAngiography extends BatchFix implements ScriptConstants {

	protected INFRA15862_RemodelFluroAngiography(BatchFix clone) {
		super(clone);
	}

	Concept insertion;
	Concept fluoroImaging;

	enum Pass { FIRST_PASS, SECOND_PASS }
	private Pass pass = Pass.FIRST_PASS;

	public static void main(String[] args) throws TermServerScriptException {
		INFRA15862_RemodelFluroAngiography fix = new INFRA15862_RemodelFluroAngiography(null);
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
		subsetECL = "<< 1296925008 |Insertion of stent (procedure)|: 260686004 |Method| = << 312275004 |Fluoroscopic imaging - action (qualifier value)|, [0..0] 363703001 |Has intent (attribute)| = 429892002 |Guidance intent (qualifier value)|";
		insertion = gl.getConcept("257867005 |Insertion - action (qualifier value)|");
		fluoroImaging = gl.getConcept("312275004 |Fluoroscopic imaging - action (qualifier value)|");
		String[] columnHeadings = new String[]{
				"Task Key, Task Description, Concept SCTID,FSN, ,Severity, Action, Details, Details, Details, , ,",
				"Concept SCTID,FSN,SemTag, Issue, Details, Expression,"};
		String[] tabNames = new String[]{
				"Processed",
				"Excluded"};
		super.postInit(tabNames, columnHeadings, false);
	}

	public int doFixSafely(Concept c) {
		try {
			return doFix(null, c, null);
		} catch (TermServerScriptException e) {
			reportSafely(SECONDARY_REPORT, c, "Processing failure", e.getMessage(), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			return NO_CHANGES_MADE;
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = task == null? concept.clone() : loadConcept(concept, task.getBranchPath());
		Concept device = identifyDeviceInInsertionRG(loadedConcept);
		if (device != null) {
			changesMade += enhanceImagingGroup(task, loadedConcept, device);
		} else if (pass == Pass.FIRST_PASS) {
			report(SECONDARY_REPORT, loadedConcept, "Failed to identify device in insertion RG", concept.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
		}
		if (changesMade > 0 && pass == Pass.SECOND_PASS) {
			updateConcept(task, loadedConcept, info);
			report(task, loadedConcept, Severity.NONE, ReportActionType.INFO, loadedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		}
		return changesMade;
	}

	private Concept identifyDeviceInInsertionRG(Concept c) throws TermServerScriptException {
		RelationshipGroup insertionGroup = identifyGroupContaining(c, "insertion", METHOD, insertion);
		return insertionGroup.getValueForType(DIRECT_DEVICE);
	}

	private RelationshipGroup identifyGroupContaining(Concept c, String groupIdentifier, Concept type, Concept value) throws TermServerScriptException {
		List<RelationshipGroup> matchingGroups = new ArrayList<>();
		for (RelationshipGroup rg : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			for (Relationship r : rg.getRelationships()) {
				if (r.getType().equals(type) && isTypeOf(r.getTarget(), value)) {
					matchingGroups.add(rg);
					break;
				}
			}
		}
		if (matchingGroups.isEmpty()) {
			throw new TermServerScriptException("No " + groupIdentifier + " groups found");
		} else if (matchingGroups.size() > 1) {
			throw new TermServerScriptException("Multiple " + groupIdentifier + " groups found");
		}
		return matchingGroups.get(0);
	}

	private int enhanceImagingGroup(Task t, Concept c, Concept device) throws TermServerScriptException {
		int changesMade = 0;
		RelationshipGroup groupToEnhance = identifyGroupContaining(c, "imaging", METHOD, fluoroImaging);
		changesMade += addRelationshipIfRequired(t, c, groupToEnhance, HAS_INTENT, gl.getConcept("429892002 |Guidance intent (qualifier value)|"));
		changesMade += addRelationshipIfRequired(t, c, groupToEnhance, DIRECT_DEVICE, device);
		return changesMade;
	}

	private int addRelationshipIfRequired(Task t, Concept c, RelationshipGroup g, Concept type, Concept value) throws TermServerScriptException {
		Concept existingType = g.getValueForType(type, true);
		if (existingType == null) {
			RelationshipTemplate rel = new RelationshipTemplate(type, value, CharacteristicType.STATED_RELATIONSHIP);
			return addRelationship(t, c, rel, g.getGroupId(), RelationshipTemplate.Mode.UNIQUE_TYPE_IN_THIS_GROUP);
		}
		return NO_CHANGES_MADE;
	}

	private boolean isTypeOf(Concept target, Concept superType) throws TermServerScriptException {
		return target.equals(superType) || target.getAncestors(NOT_SET).contains(superType);
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		reportChangesWithoutTask = false;
		List<Component> componentsToProcess = findConcepts(subsetECL)
				.stream()
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.filter(c -> doFixSafely(c) > NO_CHANGES_MADE)
				.collect(Collectors.toList());
		pass = Pass.SECOND_PASS;
		return componentsToProcess;
	}

}
