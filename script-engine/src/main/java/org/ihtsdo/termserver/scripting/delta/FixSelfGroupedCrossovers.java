package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

public class FixSelfGroupedCrossovers extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(FixSelfGroupedCrossovers.class);

	private final List<Concept> hierarchies = new ArrayList<>();

	private final List<Concept> skipAttributeTypes = new ArrayList<>();

	private List<Concept> allowRepeatingTypes = new ArrayList<>();

	private Concept COMPONENT;

	//private final int BatchSize = 25;
	private final int BatchSize = 99999;

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		FixSelfGroupedCrossovers delta = new FixSelfGroupedCrossovers();
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
		//hierarchies.add(OBSERVABLE_ENTITY);
		hierarchies.add(gl.getConcept("386053000 |Evaluation procedure|"));

		skipAttributeTypes.add(gl.getConcept("363702006 |Has focus (attribute)|"));
		skipAttributeTypes.add(IS_A);
		skipAttributeTypes.add(PART_OF);

		COMPONENT = gl.getConcept("246093002 |Component (attribute)|");

		allowRepeatingTypes = List.of(
				gl.getConcept("704321009 |Characterizes (attribute)| "),
				COMPONENT
		);

		//We're not moving modules here, so set target module to be null so we don't change it
		targetModuleId = null;

		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Before (Stated), Before (Inferred), After (Stated), ",
				"SCTID, FSN, SemTag, Expression",
				"SCTID, FSN, SemTag, Before, After",
		};

		String[] tabNames = new String[]{
				"Fixed Crossover",
				"No changes required",
				"Repeated Attribute - Illegal",
		};
		postInit(tabNames, columnHeadings, false);
	}

	private int process() throws ValidationFailure, TermServerScriptException, IOException {
		int conceptsInThisBatch = 0;
		for (Concept hierarchy : hierarchies) {
			for (Concept c :  SnomedUtils.sort(hierarchy.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP))) {
				/*if (c.getId().equals("407715006")) {
					LOGGER.debug("Debug here");
				}*/
				if (inScope(c)) {
					String beforeStated = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
					String beforeInferred = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
					restateInferredRelationships(c);
					removeRedundandGroups((Task) null, c);
					c.recalculateGroups();
					int reportToTabIdx = fixSelfGroupedCrossover(c, beforeStated, beforeInferred);
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
		}
		return conceptsInThisBatch;
	}

	private int fixSelfGroupedCrossover(Concept c, String beforeStated, String beforeInferred) throws TermServerScriptException {
		int reportToTabIdx = PRIMARY_REPORT;
		boolean changesMade = false;
		List<RelationshipGroup> populatedGroups = identifyGroups(c, false);
		List<RelationshipGroup> selfGroups = identifyGroups(c, true);
		Set<RelationshipGroup> groupsToRemove = new HashSet<>();
		
		//Work through all the populated groups and see if we can match an
		//attribute type with a self grouped relationship where the populated 
		//group is an ancestor of the self grouped value.   Phew!  Make sense?
		for (RelationshipGroup populatedGroup : populatedGroups) {
			for (Relationship r : populatedGroup.getRelationships()) {
				for (RelationshipGroup selfGroup : selfGroups) {
					if (containsSkippedAttribute(selfGroup)) {
						continue;
					}
					for (Relationship r2 : selfGroup.getRelationships()) {
						if (resolvesCrossover(r, r2)) {
							r.setTarget(r2.getTarget());
							r.setDirty();
							report(PRIMARY_REPORT, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, r);
							changesMade = true;
							groupsToRemove.add(selfGroup);
						}
					}
				}
			}
		}

		for (RelationshipGroup groupToRemove : groupsToRemove) {
			removeRelationshipGroup((Task)null, c, groupToRemove);
		}
		
		String after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		boolean isIllegal = reportIllegalGrouping(c, beforeStated, beforeInferred, after);
		
		if (isIllegal) {
			reportToTabIdx = TERTIARY_REPORT;
			c.setClean();
		} else {
			if (changesMade) {
				c.recalculateGroups();
				int groupCount = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false).size();
				report(c, Severity.LOW, ReportActionType.INFO, beforeStated, beforeInferred, after, groupCount);
			} else {
				report(SECONDARY_REPORT, c, after);
				reportToTabIdx = SECONDARY_REPORT;
			}
		}
		return reportToTabIdx;
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

	private boolean resolvesCrossover(Relationship generalCase, Relationship specificCase) throws TermServerScriptException {
		//Only need to consider where these relationships are of the same type
		if (!generalCase.getType().equals(specificCase.getType())) {
			return false;
		}
		
		//Now we care about whether the general case is an ancestor of the specific case
		Concept generalTarget = generalCase.getTarget();
		Concept specificTarget = specificCase.getTarget();
		Set<Concept> ancestorsAndSelf = specificTarget.getAncestors(NOT_SET);
		ancestorsAndSelf.add(specificTarget);
		return ancestorsAndSelf.contains(generalTarget);
	}

	private List<RelationshipGroup> identifyGroups(Concept c, boolean selfGrouped) {
		//Determine if group is self grouped, and then return the set which either matches that, or does not match
		return c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)
				.stream()
				.filter(g -> isSelfGrouped(g) == selfGrouped)
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
		//of which one or more is a self grouped attribute
		boolean hasSelfGroupedAttribute = false;
		boolean hasOtherGroup = false;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, false)) {
			//Ignore group 0 
			if (g.getGroupId() == 0) {
				continue;
			}
			if (g.size() == 1) {
				//Skip attribute types we're ignoring
				if (containsSkippedAttribute(g)) {
					continue;
				}
				hasSelfGroupedAttribute = true;
			} else {
				hasOtherGroup = true;
			}
			
			if (hasSelfGroupedAttribute && hasOtherGroup) {
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

}
