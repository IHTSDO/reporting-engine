package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-10767 Combine role groups containing Incision + drainage methods
 * into a single role group using the combined value - 56783008 |Incision AND drainage (procedure)|
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeAttributeTypes extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(MergeAttributeTypes.class);

	Concept findAttributeTypeA;
	Concept findAttributeTypeB;
	Concept replacementAttributeType;
	
	protected MergeAttributeTypes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		MergeAttributeTypes fix = new MergeAttributeTypes(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, Severity, Action, Details, , , ",
				"Concept, Issue, Expression, Details, , , "};
		String[] tabNames = new String[] {"Processing", "Oddities"};
		
		subsetECL = "< 56783008 |Incision AND drainage (procedure)|";
		findAttributeTypeA = gl.getConcept("129287005 |Incision - action|");
		findAttributeTypeB = gl.getConcept("129289008 |Drainage - action|");
		replacementAttributeType = gl.getConcept("1284859006 |Surgical drainage - action (qualifier value)|");
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = mergeAttributes(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int mergeAttributes(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		RelationshipGroup[] relevantGroups = identifyABGroup(c);
		RelationshipGroup keep = relevantGroups[0];
		RelationshipGroup delete = relevantGroups[1];
		for (Relationship r : delete.getRelationships()) {
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, r);
			c.removeRelationship(r);
			changesMade++;
		}
		Relationship replaceRel = keep.getRelationshipWithType(findAttributeTypeA);
		Relationship mergedRel = replaceRel.clone();
		mergedRel.setType(replacementAttributeType);
		changesMade++;
		//Now work through the group we're removing and see if there's anything from 'delete' that we need in 'keep'
		for (Relationship r : delete.getRelationships()) {
			if (r.getType().equals(findAttributeTypeB)) {
				continue;
			}
			if (!keep.containsTypeValue(r)) {
				//Do we already have a relationship of this type
				if (keep.containsType(r.getType())) {
					throw new ValidationFailure(c, "Clash in groups " + keep.getGroupId() + " & " + delete.getGroupId() + " for " + r.getType().toStringPref());
				}
			}
			r.setGroupId(keep.getGroupId());
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "Moved group " + keep.getGroupId() + " --> " + delete.getGroupId(), r);
			changesMade++;
		}
		return changesMade;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		return SnomedUtils.sort(findConcepts(subsetECL)).stream()
			.filter(c -> meetsProcessingCriteria(c))
			.collect(Collectors.toList());
	}
	
	private boolean meetsProcessingCriteria(Concept c) {
		if (c.getId().equals("284181007")) {
			LOGGER.debug("here");
		}
		try {
			RelationshipGroup[] groupAB = identifyABGroup(c);
			return groupAB[0] != null && groupAB[1] != null;
		} catch (Exception e) {
			reportSafely(SECONDARY_REPORT, c, e.getMessage(), c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		}
		return false;
	}

	private RelationshipGroup[] identifyABGroup(Concept c) throws TermServerScriptException {
		return new RelationshipGroup[] { getRelationshipGroups(c, findAttributeTypeA),
				getRelationshipGroups(c, findAttributeTypeB)};
	}

	private RelationshipGroup getRelationshipGroups(Concept c, Concept type) throws TermServerScriptException {
		List<RelationshipGroup> groups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, type);
		if (groups.isEmpty()) {
			return null;
		} else if (groups.size() > 1) {
			throw new TermServerScriptException("Concept features multiple groups containing " + type);
		}
		return groups.get(0);
	}

}
