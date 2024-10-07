package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class GroupSelfGroupedAttributes extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(GroupSelfGroupedAttributes.class);

	private List<String> eclSelections = new ArrayList<>();
	private final List<Concept> skipAttributeTypes = new ArrayList<>();
	private List<Concept> singleTypes = new ArrayList<>();
	private List<Concept> allowRepeatingTypes = new ArrayList<>();
	private Concept COMPONENT;
	private final int BatchSize = 99999;
	private int conceptsInThisBatch = 0;

	public static void main(String[] args) throws TermServerScriptException {
		GroupSelfGroupedAttributes delta = new GroupSelfGroupedAttributes();
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"); //Ad-Hoc Batch Updates
			delta.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			delta.getArchiveManager().setRunIntegrityChecks(false);
			delta.newIdsRequired = false; // We'll only be modifying existing components
			delta.sourceModuleIds = Set.of("1326031000000103","83821000000107","999000011000000103","999000011000001104","999000021000000109","999000021000001108","999000031000000106","999000041000000102");
			delta.init(args);
			delta.loadProjectSnapshot();
			delta.postInit();
			delta.process();
			delta.createOutputArchive(false, delta.conceptsInThisBatch);
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		eclSelections.add("<< " + OBSERVABLE_ENTITY.getConceptId());

		skipAttributeTypes.add(gl.getConcept("363702006 |Has focus (attribute)|"));
		skipAttributeTypes.add(IS_A);
		skipAttributeTypes.add(PART_OF);

		COMPONENT = gl.getConcept("246093002 |Component (attribute)|");

		singleTypes = List.of(
				gl.getConcept("370130000 |Property (attribute)|"),
				gl.getConcept("370132008 |Scale type (attribute)|"),
				gl.getConcept("370134009 |Time aspect (attribute)|"));

		allowRepeatingTypes = List.of(
				gl.getConcept("704321009 |Characterizes (attribute)| "),
				COMPONENT
		);

		//We're not moving modules here, so don't set the targetModuleId so that it remains unchanged
		targetModuleId = null;

		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Before, After, Group Count, Has Repeated Attribute Type,",
				"SCTID, FSN, SemTag, Expression",
				"SCTID, FSN, SemTag, Before, After",
				"SCTID, FSN, SemTag, Before, After"
		};

		String[] tabNames = new String[]{
				"SelfGroupedAttributes - Grouped",
				"No changes required",
				"Repeated Attribute - Illegal",
				"Repeated Attribute - Singles"
		};
		postInit(tabNames, columnHeadings);
	}

	@Override
	protected void process() throws TermServerScriptException {
		for (String eclSelection : eclSelections) {
			for (Concept c : SnomedUtils.sort(findConcepts(eclSelection))) {
				if (inScope(c)) {
					processConcept(c);
				}
			}
		}
	}

	private void processConcept(Concept c) throws TermServerScriptException {
		String before = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		restateInferredRelationships(c);
		removeRedundandGroups((Task) null, c);
		c.recalculateGroups();
		int reportToTabIdx = groupSelfGroupedAttributes(c, before);
		if (reportToTabIdx == PRIMARY_REPORT) {
			outputRF2(c, true);
			conceptsInThisBatch++;
			if (conceptsInThisBatch >= BatchSize) {
				createOutputArchive(false, conceptsInThisBatch);
				gl.setAllComponentsClean();
				outputDirName = "output"; //Reset so we don't end up with _1_1_1
				initialiseOutputDirectory();
				initialiseFileHeaders();
				conceptsInThisBatch = 0;
			}
		} else {
			LOGGER.debug("Concept reason for no change already recorded in tab {} for {}", reportToTabIdx, c);
		}
		
	}

	private int groupSelfGroupedAttributes(Concept c, String before) throws TermServerScriptException {
		int reportToTabIdx = PRIMARY_REPORT;
		boolean changesMade = false;
		Set<Concept> typesSeen = new HashSet<>();
		int viableGroup = calculateViableGroup(c);
		Set<Relationship> mergeIntoAxiom = new HashSet<>();
		AxiomEntry axiomEntry = null;
		boolean hasMultipleSameType = false;
		boolean hasIllegalMultipleSameType = false;
		boolean hasRepeatedSingleType = false;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (skipAttributeTypes.contains(r.getType())) {
				continue;
			}

			if (typesSeen.contains(r.getType())) {
				hasMultipleSameType = true;
				if (!allowRepeatingTypes.contains(r.getType())) {
					hasIllegalMultipleSameType = true;
					if (singleTypes.contains(r.getType())) {
						hasRepeatedSingleType = true;
					}
				}
			} else {
				typesSeen.add(r.getType());
			}

			r.setGroupId(viableGroup);
			r.setDirty();
			if (r.getAxiomEntry() != null) {
				axiomEntry = r.getAxiomEntry();
				r.getAxiomEntry().setDirty();
			} else {
				mergeIntoAxiom.add(r);
			}
			changesMade = true;
		}

		if (changesMade) {
			//Any relationships that were copied over from inferred need to be merged into the axiom
			if (axiomEntry != null) {
				for (Relationship r : mergeIntoAxiom) {
					r.setAxiomEntry(axiomEntry);
					r.setDirty();
				}
			}

			//We need to reset relationship groups
			c.recalculateGroups();
			String after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			if (before.equals(after)) {
				report(SECONDARY_REPORT, c, after);
				reportToTabIdx = SECONDARY_REPORT;
			} else if (hasIllegalMultipleSameType) {
				int tabIdx = TERTIARY_REPORT;  //Default to illegal
				if (hasRepeatedSingleType) {
					tabIdx = QUATERNARY_REPORT;
				}
				report(tabIdx, c, before, after);
				reportToTabIdx = tabIdx;
			} else {
				int groupCount = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false).size();
				report(c, Severity.LOW, ReportActionType.INFO, before, after, groupCount, hasMultipleSameType?"Y":"N");
			}
		} else {
			reportToTabIdx = SECONDARY_REPORT;
		}
		return reportToTabIdx;
	}

	private int calculateViableGroup(Concept c) {
		int viableGroup = 1;
  		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)) {
			for (Relationship r : g.getRelationships()) {
				if (skipAttributeTypes.contains(r.getType())) {
					viableGroup++;
				}
			}
			//Is group 1 viable?  That's our preference
			if (viableGroup == 1) {
				return viableGroup;
			}
		}
		return viableGroup;
	}

/*	private boolean inScope(Concept c) throws TermServerScriptException {
		if (!c.getConceptId().equals("699126008")) {
			return false;
		}

		//Are we in scope more generally?
		if (!super.inScope(c)) {
			return false;
		}

		//But also, we're looking for concepts that have more the one self grouped attribute
		boolean hasSelfGroupedAttribute = false;

		//Work through both existing stated grouped, plus any that would need to be copied over from the inferred view
		List<RelationshipGroup> combinedGroups = determineInferredRelGroupsToBeStated(c);
		combinedGroups.addAll(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false));
		for (RelationshipGroup g : combinedGroups) {
			//Ignore group 0
			if (g.getGroupId() == 0) {
				continue;
			}
			if (g.size() == 1) {
				//Have we already seen a self grouped attribute?
				if (hasSelfGroupedAttribute) {
					return true;
				}
				hasSelfGroupedAttribute = true;
			}
		}
		return false;
	}*/
	
	private boolean inScope(Concept c) {
		//Are we in scope more generally?
 		if (!super.inScope(c)) {
			return false;
		}

		//But also, we're looking for concepts that have more than one group
		//where one group (which we're not skipping) is self grouped
		boolean containsSelfGroup = false;
		boolean containsOtherGroup = false;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, false)) {
			//Ignore group 0 
			if (g.getGroupId() == 0) {
				continue;
			}
			if (g.size() == 1) {
				//Skip attribute types we're ignoring
				if (containsSkippedAttribute(g)) {
					continue;
				} else {
					//If we _already_ have a self group, then two self grouped attributes
					//Meets our criteria
					if (containsSelfGroup) {
						return true;
					}
					containsSelfGroup = true;
				}
			} else {
				containsOtherGroup = true;
			}
			
			if (containsSelfGroup && containsOtherGroup) {
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


	public List<RelationshipGroup> determineInferredRelGroupsToBeStated(Concept c) throws TermServerScriptException {
		//Work through all inferred groups and collect any that aren't also stated, to state
		List<RelationshipGroup> toBeStated = new ArrayList<>();
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);

		nextInferredGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			boolean matchFound = false;
			for (RelationshipGroup statedGroup : statedGroups) {
				if (inferredGroup.equals(statedGroup)) {
					matchFound = true;
					continue nextInferredGroup;
				}
			}
			if (!matchFound) {
				toBeStated.add(inferredGroup);
			}
		}
		return toBeStated;
	}

}
