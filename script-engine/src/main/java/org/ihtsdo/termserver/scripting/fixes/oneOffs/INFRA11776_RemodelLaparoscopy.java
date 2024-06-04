package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * So pretty much, we want to remove the default surgical action / procedure site where
 * it has not been developed from the root concept, and replace it with incision + anterior abdo wall.
 * But if it _has_ been developed, then we can just add the new RG.
 */
public class INFRA11776_RemodelLaparoscopy extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA11776_RemodelLaparoscopy.class);

	private Set<String> exclusionTexts;
	private List<RelationshipGroup> addGroups = new ArrayList<>();
	private RelationshipGroup matchGroup;

	protected INFRA11776_RemodelLaparoscopy(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA11776_RemodelLaparoscopy fix = new INFRA11776_RemodelLaparoscopy(null);
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
		subsetECL = "(< 71388002 |Procedure| : << 424226004 |using device| = << 86174004 |laparoscope)| ) MINUS << 73632009 |Laparoscopy|";
		matchGroup = new RelationshipGroup(NOT_SET);
		matchGroup.addRelationship(new RelationshipTemplate(METHOD, gl.getConcept("129284003 |Surgical action|")));
		matchGroup.addRelationship(new RelationshipTemplate(PROCEDURE_SITE, gl.getConcept("818983003 |Abdomen|")));

		RelationshipGroup addGroup = new RelationshipGroup(RF2Constants.NOT_SET);
		addGroup.addRelationship(new RelationshipTemplate(METHOD, gl.getConcept("129287005 |Incision - action (qualifier value)|")));
		addGroup.addRelationship(new RelationshipTemplate(ScriptConstants.PROCEDURE_SITE_DIRECT, gl.getConcept("59380008 |Anterior abdominal wall structure (body structure)|")));
		addGroups.add(addGroup);

		addGroup = new RelationshipGroup(RF2Constants.NOT_SET);
		addGroup.addRelationship(new RelationshipTemplate(METHOD, gl.getConcept("129433002 |inspection|")));
		addGroup.addRelationship(new RelationshipTemplate(ScriptConstants.PROCEDURE_SITE, gl.getConcept("818983003 |Structure of abdominopelvic cavity and/or content of abdominopelvic cavity and/or anterior abdominal wall|")));
		addGroup.addRelationship(new RelationshipTemplate(ScriptConstants.USING_DEVICE, gl.getConcept("86174004 |laparoscope|")));
		addGroups.add(addGroup);

		exclusionTexts = new HashSet<>();
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade += addOrReplaceRoleGroup(task, loadedConcept);
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

	private int addOrReplaceRoleGroup(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		boolean matchFound = false;
		int useGroup = SnomedUtils.getFirstFreeGroup(c);
		RelationshipGroup matchingGroup = SnomedUtils.findMatchingGroup(c, matchGroup, CharacteristicType.STATED_RELATIONSHIP);
		if (matchingGroup != null) {
			useGroup = matchingGroup.getGroupId();
			removeRelationshipGroup(t, c, matchingGroup);
			changesMade++;
		}

		for (RelationshipGroup addGroup : addGroups) {
			changesMade += addRelationshipGroup(t, c, addGroup, useGroup);
			useGroup = SnomedUtils.getFirstFreeGroup(c);
		}

		report(t, c, Severity.LOW, ReportActionType.INFO, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		return changesMade;
	}

	private int addRelationshipGroup(Task t, Concept c, RelationshipGroup addGroup, int useGroup) throws TermServerScriptException {
		int changesMade = NO_CHANGES_MADE;
		addGroup.setGroupId(useGroup);
		//TODO Check if we actually make changes here
		//To do ancestor check, use local copy of concept to ensure it is populated into local hierarchy
		Concept localConcept = gl.getConcept(c.getConceptId());
		if (SnomedUtils.findMatchingOrDescendantGroup(localConcept, addGroup, CharacteristicType.STATED_RELATIONSHIP) != null) {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Group (or more specific variant) already present");
		} else {
			changesMade += c.addRelationshipGroup(addGroup, null);
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_ADDED);
		}
		return changesMade;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		return isExcluded(fsn);
	}
	
	private boolean isExcluded(String term) {
		for (String exclusionWord : exclusionTexts) {
			if (term.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return findConcepts(subsetECL)
				.stream()
				.filter(this::inScope)
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.filter(c -> !isExcluded(c))
				.filter(c -> !alreadyFeaturesGroupsToBeAdded(c))
				.collect(Collectors.toList());
	}

	private boolean alreadyFeaturesGroupsToBeAdded(Concept c) {
		for (RelationshipGroup addGroup : addGroups) {
			if (SnomedUtils.findMatchingGroup(c, addGroup, CharacteristicType.STATED_RELATIONSHIP) == null) {
				return false;
			}
		}
		return true;
	}
}
