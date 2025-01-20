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

public class MergeAttributeValues extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(MergeAttributeValues.class);

	Concept attributeType = Concept.METHOD;
	Concept findAttributeValueA;
	Concept findAttributeValueB;
	Concept replacementAttributeValue;
	
	protected MergeAttributeValues(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		MergeAttributeValues fix = new MergeAttributeValues(null);
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
		findAttributeValueA = gl.getConcept("129287005 |Incision - action|");
		findAttributeValueB = gl.getConcept("129289008 |Drainage - action|");
		replacementAttributeValue = gl.getConcept("1284859006 |Surgical drainage - action (qualifier value)|");
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = mergeAttributes(task, loadedConcept);
			shuffleDown(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
			report(task, concept, Severity.LOW, ReportActionType.INFO, loadedConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
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
			removeRelationship(t, c, r);
			changesMade++;
		}
		Relationship replaceRel = keep.getRelationshipWithTypeValue(attributeType, findAttributeValueA);
		Relationship mergedRel = replaceRel.clone();
		mergedRel.setTarget(replacementAttributeValue);
		removeRelationship(t, c, replaceRel);
		addRelationship(t, c, mergedRel);
		//report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, mergedRel);
		changesMade++;
		
		//Now work through the group we're removing and see if there's anything from 'delete' that we need in 'keep'
		for (Relationship r : delete.getRelationships()) {
			if (r.getTarget().equals(findAttributeValueB)) {
				continue;
			}
			if (!keep.containsTypeValue(r)) {
				//Do we already have a relationship of this type
				if (keep.containsType(r.getType())) {
					throw new ValidationFailure(c, "Clash in groups " + keep.getGroupId() + " & " + delete.getGroupId() + " for attribute type " + gl.getConcept(r.getType().getId()).toStringPref());
				}
			}
			r.setGroupId(keep.getGroupId());
			r.setActive(true);
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "Moved from group " + keep.getGroupId() + " --> " + delete.getGroupId() + "\n" + r);
			changesMade++;
		}
		c.recalculateGroups();
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
		return new RelationshipGroup[] { getRelationshipGroups(c, findAttributeValueA),
				getRelationshipGroups(c, findAttributeValueB)};
	}

	private RelationshipGroup getRelationshipGroups(Concept c, Concept attributeValue) throws TermServerScriptException {
		List<RelationshipGroup> groups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, attributeType, attributeValue );
		if (groups.isEmpty()) {
			return null;
		} else if (groups.size() > 1) {
			throw new TermServerScriptException("Concept features multiple groups containing " + attributeValue);
		}
		return groups.get(0);
	}

}
