package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class ReplaceAttributeValueInGroup extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceAttributeValueInGroup.class);
	private final String ecl = "<< 142505009 |Cystoscopy (procedure)|";
	private RelationshipGroup targetGroup;
	private RelationshipTemplate replaceAttributeTemplate;

	protected ReplaceAttributeValueInGroup(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceAttributeValueInGroup fix = new ReplaceAttributeValueInGroup(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			//fix.taskPrefix = "Observable ";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		targetGroup = SnomedUtils.createRelationshipGroup(gl,
				new String[][] {
						{"260686004 |Method (attribute)|","129433002 |Inspection - action (qualifier value)|"},
						{"424226004 |Using device (attribute)|","20613002 |Cystoscope, device (physical object)|"},
						{"363704007 |Procedure site (attribute)|","19787009 |Lower urinary tract structure (body structure)|"}}
		);
		replaceAttributeTemplate = SnomedUtils.createRelationshipTemplate(gl,"363704007 |Procedure site (attribute)|","19787009 |Lower urinary tract structure (body structure)|");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept localConcept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(localConcept, task.getBranchPath());
			changesMade = switchValues(task, loadedConcept, localConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, localConcept, v);
		} catch (Exception e) {
			report(task, localConcept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept loadedConcept, Concept localConcept) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		//Find the group that matches the target, and replace the attribute value
		RelationshipGroup group = SnomedUtils.findMatchingOrDescendantGroup(localConcept, targetGroup, CharacteristicType.STATED_RELATIONSHIP);
		if (group == null) {
			throw new ValidationFailure(loadedConcept, "Unable to find group to replace");
		}
		//Find the matching attribute (might be a descendent) via the type
		for (Relationship r : group.getRelationships()) {
			if (r.getType().equals(replaceAttributeTemplate.getType()) ||
					r.getType().getAncestors(NOT_SET).contains(replaceAttributeTemplate.getType())) {
				//BUT it might be that the original attribute had a more specific type, keep that.
				RelationshipTemplate rt = replaceAttributeTemplate.clone();
				rt.setType(r.getType());
				changesMade = replaceRelationship(t, loadedConcept, r, rt);
				break;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> allAffected = new ArrayList<>();
		LOGGER.info("Identifying concepts to process");
		List<Concept> concepts = SnomedUtils.sort(findConcepts(ecl));
		//List<Concept> concepts = List.of(gl.getConcept("142505009 |Cystoscopy (procedure)|"));
				nextConcept:
		for (Concept c : concepts) {
			//We want a group that matches the target as descendents, but doesn't _exactly_ match
			//or there is not need to make changes
			if (SnomedUtils.findMatchingOrDescendantGroup(c, targetGroup, CharacteristicType.STATED_RELATIONSHIP) != null &&
				SnomedUtils.findMatchingGroup(c, targetGroup, CharacteristicType.STATED_RELATIONSHIP) == null) {
				allAffected.add(c);
			}
		}
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}
}
