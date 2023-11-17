package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
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
	private RelationshipGroup addGroup;
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
		subsetECL = "<< 73632009 |Laparoscopy (procedure)|";
		matchGroup = new RelationshipGroup(NOT_SET);
		matchGroup.addRelationship(new RelationshipTemplate(METHOD, gl.getConcept("129284003 |Surgical action|")));
		matchGroup.addRelationship(new RelationshipTemplate(PROCEDURE_SITE, gl.getConcept("818983003 |Abdomen|")));

		addGroup = new RelationshipGroup(RF2Constants.NOT_SET);
		addGroup.addRelationship(new RelationshipTemplate(METHOD, gl.getConcept("129287005 |Incision - action (qualifier value)|")));
		addGroup.addRelationship(new RelationshipTemplate(ScriptConstants.PROCEDURE_SITE_DIRECT, gl.getConcept("59380008 |Anterior abdominal wall structure (body structure)|")));

		exclusionTexts = new HashSet<>();
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			//changesMade = modifyDescriptions(task, loadedConcept);
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

	/*private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			switch (d.getType()) {
				case FSN : changesMade += modifyFSN(t, c);
							break;
				case SYNONYM :  if (!isExcluded(d.getTerm().toLowerCase())) {
									String replacement = d.getTerm() + " with contrast";
									replaceDescription(t, c, d, replacement, null);
									changesMade++;
								};
								break;
				default : 
			}
		}
		return changesMade;
	}

	private int modifyFSN(Task t, Concept c) throws TermServerScriptException {
		String[] fsnParts = SnomedUtils.deconstructFSN(c.getFsn());
		String replacement = fsnParts[0] + " with contrast " + fsnParts[1];
		replaceDescription(t, c, c.getFSNDescription(), replacement, null);
		return CHANGE_MADE;
	}*/

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
		addGroup.setGroupId(useGroup);
		//TODO Check if we actually make changes here
		changesMade += c.addRelationshipGroup(addGroup, null	);
		report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_ADDED);
		report(t, c, Severity.LOW, ReportActionType.INFO, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
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
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.filter(c -> !isExcluded(c))
				.collect(Collectors.toList());
	}
}
