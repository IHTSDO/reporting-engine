package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupSelfGroupedAttributesTargeted extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(GroupSelfGroupedAttributesTargeted.class);

	private String eclSelection = "";

	private Concept targetSingleGroupAttributeType;

	private final List<Concept> skipAttributeTypes = new ArrayList<>();

	private List<Concept> allowRepeatingTypes = new ArrayList<>();

	private Concept COMPONENT;

	private final int BatchSize = 99999;

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		GroupSelfGroupedAttributesTargeted delta = new GroupSelfGroupedAttributesTargeted();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.getArchiveManager(true).setPopulateReleasedFlag(true);
			delta.getArchiveManager(true).setRunIntegrityChecks(false);
			delta.newIdsRequired = false; // We'll only be modifying existing components
			delta.init(args);
			delta.loadProjectSnapshot();
			delta.postInit();
			int lastBatchSize = delta.process();
			delta.createOutputArchive(false, lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		eclSelection = " << 386053000 |Evaluation procedure| : 116686009 |Has specimen (attribute)| = *";

		targetSingleGroupAttributeType = gl.getConcept("116686009 |Has specimen (attribute)|");

		skipAttributeTypes.add(gl.getConcept("363702006 |Has focus (attribute)|"));
		skipAttributeTypes.add(IS_A);
		skipAttributeTypes.add(PART_OF);

		COMPONENT = gl.getConcept("246093002 |Component (attribute)|");

		allowRepeatingTypes = List.of(
				gl.getConcept("704321009 |Characterizes (attribute)| "),
				COMPONENT
		);

		//We're not moving modules here, so the source and target modules are the same
		targetModuleId = moduleId;

		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Before (Stated), Before (Inferred), After (Stated), ",
				"SCTID, FSN, SemTag, Message, Expression,",
				"SCTID, FSN, SemTag, Before, After",
		};

		String[] tabNames = new String[]{
				"Target Attribute Merged",
				"No viable changes",
				"Repeated Attribute - Illegal",
		};
		postInit(tabNames, columnHeadings, false);
	}

	private int process() throws ValidationFailure, TermServerScriptException, IOException {
		int conceptsInThisBatch = 0;
		for (Concept c :  SnomedUtils.sort(findConcepts(eclSelection))) {
			/*if (c.getId().equals("407715006")) {
				LOGGER.debug("Debug here");
			}*/
			if (inScope(c)) {
				String beforeStated = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				String beforeInferred = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				restateInferredRelationships(c);
				removeRedundandGroups((Task) null, c);
				c.recalculateGroups();
				int reportToTabIdx = mergeTargetedAttribute(c, beforeStated, beforeInferred);
				if (reportToTabIdx == PRIMARY_REPORT) {
					outputRF2(c, true);
					conceptsInThisBatch++;
					if (conceptsInThisBatch >= BatchSize) {
						if (!dryRun) {
							createOutputArchive(false, conceptsInThisBatch);
							outputDirName = "output"; //Reset so we don't end up with _1_1_1
							initialiseOutputDirectory();
							initialiseFileHeaders();
						}
						gl.setAllComponentsClean();
						conceptsInThisBatch = 0;
					}
				} else {
					LOGGER.debug("Concept reason for no change already recorded in tab " + reportToTabIdx + " for " + c);
				}
			}
		}
		return conceptsInThisBatch;
	}

	private int mergeTargetedAttribute(Concept c, String beforeStated, String beforeInferred) throws TermServerScriptException {
		int reportToTabIdx = PRIMARY_REPORT;
		List<RelationshipGroup> populatedGroups = identifyGroups(c);
		List<RelationshipGroup> sourceGroups = findSourceGroups(c);

		if (sourceGroups.size() == 0) {
			reportToTabIdx = SECONDARY_REPORT;
			report(reportToTabIdx, c,  "No self grouped " + targetSingleGroupAttributeType + " found", beforeInferred);
			return reportToTabIdx;
		} else if (sourceGroups.size() > 1) {
			reportToTabIdx = SECONDARY_REPORT;
			report(reportToTabIdx, c,  "Multiple self grouped " + targetSingleGroupAttributeType + " found", beforeInferred);
			return reportToTabIdx;
		}

		RelationshipGroup sourceGroup = sourceGroups.iterator().next();
		Relationship sourceAttribute = sourceGroup.getRelationships().iterator().next();

		if (populatedGroups.size() > 1) {
			reportToTabIdx = SECONDARY_REPORT;
			report(reportToTabIdx, c,  "Multiple target groups found, not supported", beforeInferred);
			return reportToTabIdx;
		}

		if (populatedGroups.size() == 0) {
			reportToTabIdx = SECONDARY_REPORT;
			report(reportToTabIdx, c,  "No receiving group identified", beforeInferred);
			return reportToTabIdx;
		}
		RelationshipGroup populatedGroup = populatedGroups.iterator().next();
		
		//Move the source group into the populated group
		sourceAttribute.setGroupId(populatedGroup.getGroupId());
		sourceAttribute.setDirty();
		report(PRIMARY_REPORT, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, sourceAttribute);
		c.recalculateGroups();
		//Need all relationships to be from the same axiom so they group togther for display and shuffling
		c.normalizeStatedRelationships();
		shuffleDown((Task)null, c);

		String after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		boolean isIllegal = reportIllegalGrouping(c, beforeStated, beforeInferred, after);
		
		if (isIllegal) {
			reportToTabIdx = TERTIARY_REPORT;
			c.setClean();
		} else {
			int groupCount = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false).size();
			report(c, Severity.LOW, ReportActionType.INFO, beforeStated, beforeInferred, after, groupCount);
		}
		return reportToTabIdx;
	}

	private List<RelationshipGroup> findSourceGroups(Concept c) {
		List<RelationshipGroup> sourceGroups = new ArrayList<>();
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			if (g.size() == 1 && containsTargetAttribute(g)) {
				sourceGroups.add(g);
			}
		}
		return sourceGroups;
	}

	private boolean reportIllegalGrouping(Concept c, String beforeStated, String beforeInferred, String after) throws TermServerScriptException {
		Set<Concept> typesSeen = new HashSet<>();
		boolean hasIllegalMultipleSameType = false;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			typesSeen.clear();
			for (Relationship r : g.getRelationships()) {
				if (skipAttributeTypes.contains(r.getType())) {
					continue;
				}

				if (typesSeen.contains(r.getType())) {
					if (!allowRepeatingTypes.contains(r.getType())) {
						hasIllegalMultipleSameType = true;
						break;
					}
				} else {
					typesSeen.add(r.getType());
				}
			}
		}
		
		if (hasIllegalMultipleSameType) {
			report(TERTIARY_REPORT, c, beforeStated, beforeInferred, after);
		}
		
		return hasIllegalMultipleSameType;
	}

	private List<RelationshipGroup> identifyGroups(Concept c) {
		//Looking for any group that is not a source group (eg Has Specimen) and not a skipped group (eg Has Focus)
		return c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)
				.stream()
				.filter(g -> !containsTargetAttribute(g))
				.filter(g -> !containsSkippedAttribute(g))
				.toList();

	}

	private boolean isSelfGrouped(RelationshipGroup rg) {
		return rg.size() == 1;
	}

	private boolean inScope(Concept c) {
		/*if (!c.getConceptId().equals("699126008")) {
			return false;
		}*/

		//Are we in scope more generally?
		if (!super.inScope(c)) {
			return false;
		}

		//But also, we're looking for concepts that have more than one group
		//and also a self group that is our target
		boolean containsTargetSelfGrouped = false;
		boolean containsOtherGroup = false;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, false)) {
			//Ignore group 0 
			if (g.getGroupId() == 0) {
				continue;
			}
			if (g.size() == 1) {
				//Skip attribute types we're ignoring
				if (containsTargetAttribute(g)) {
					containsTargetSelfGrouped = true;
				} if (containsSkippedAttribute(g)) {
					continue;
				} else {
					containsOtherGroup = true;
				}
			} else {
				containsOtherGroup = true;
			}
			
			if (containsTargetSelfGrouped && containsOtherGroup) {
				return true;
			}
		}
		return false;
	}

	private boolean containsSkippedAttribute(RelationshipGroup g) {
		for (Relationship r : g.getRelationships()) {
			if (skipAttributeTypes.contains(r.getType())) {
				return true;
			}
		}
		return false;
	}

	private boolean containsTargetAttribute(RelationshipGroup g) {
		for (Relationship r : g.getRelationships()) {
			if (r.getType().equals(targetSingleGroupAttributeType)) {
				return true;
			}
		}
		return false;
	}

}
