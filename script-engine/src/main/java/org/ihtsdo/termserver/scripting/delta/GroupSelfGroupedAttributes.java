package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupSelfGroupedAttributes extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(GroupSelfGroupedAttributes.class);

	private final List<Concept> hierarchies = new ArrayList<>();

	private final List<Concept> skipAttributeTypes = new ArrayList<>();

	private List<Concept> singleTypes = new ArrayList<>();

	private List<Concept> allowRepeatingTypes = new ArrayList<>();

	Concept COMPONENT;

	private final int BatchSize = 25;
	//private final int BatchSize = 99999;

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		GroupSelfGroupedAttributes delta = new GroupSelfGroupedAttributes();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
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
		hierarchies.add(OBSERVABLE_ENTITY);
		hierarchies.add(gl.getConcept("386053000 |Evaluation procedure|"));

		skipAttributeTypes.add(gl.getConcept("363702006 |Has focus (attribute)|"));
		skipAttributeTypes.add(IS_A);
		skipAttributeTypes.add(PART_OF);

		COMPONENT = gl.getConcept("246093002 |Component (attribute)|");

		singleTypes = List.of(
				gl.getConcept("370130000 |Property (attribute)|"),
				gl.getConcept("370132008 |Scale type (attribute)|"),
				gl.getConcept("370134009 |Time aspect (attribute)|"));

		allowRepeatingTypes = List.of(
				gl.getConcept("704321009 |Characterizes (attribute)| ")
		);

		//We're not moving modules here, so the source and target modules are the same
		targetModuleId = moduleId;

		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Before, After, Group Count, Has Repeated Attribute Type,",
				"SCTID, FSN, SemTag, Expression",
				"SCTID, FSN, SemTag, Before, After",
				"SCTID, FSN, SemTag, Before, After",
				"SCTID, FSN, SemTag, Before, After"
		};

		String[] tabNames = new String[]{
				"SelfGroupedAttributes - Grouped",
				"No changes made",
				"Repeated Attribute - Illegal",
				"Repeated Attribute - Component",
				"Repeated Attribute - Singles"
		};
		postInit(tabNames, columnHeadings, false);
	}

	private int process() throws ValidationFailure, TermServerScriptException, IOException {
		int conceptsInThisBatch = 0;
		for (Concept hierarchy : hierarchies) {
			for (Concept c :  SnomedUtils.sort(hierarchy.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP))) {
				if (inScope(c)) {
					String before = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
					restateInferredRelationships(c);
					boolean changesMade = groupSelfGroupedAttributes(c, before);
					if (changesMade) {
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
						report(SECONDARY_REPORT, c, before);
					}
				}
			}
		}
		return conceptsInThisBatch;
	}

	private boolean groupSelfGroupedAttributes(Concept c, String before) throws TermServerScriptException {
		boolean changesMade = false;
		Set<Concept> typesSeen = new HashSet<>();
		int viableGroup = calculateViableGroup(c);
		Set<Relationship> mergeIntoAxiom = new HashSet<>();
		AxiomEntry axiomEntry = null;
		boolean hasMultipleSameType = false;
		boolean hasIllegalMultipleSameType = false;
		boolean hasRepeatedComponentType = false;
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
					} else if (r.getType().equals(COMPONENT)) {
						hasRepeatedComponentType = true;
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
			boolean proceedToDelta = false;
			if (before.equals(after)) {
				report(SECONDARY_REPORT, c, after);
			} else if (hasIllegalMultipleSameType) {
				int tabIdx = TERTIARY_REPORT;  //Default to illegal
				if (hasRepeatedComponentType) {
					tabIdx = QUATERNARY_REPORT;
				} else if (hasRepeatedSingleType) {
					tabIdx = QUINARY_REPORT;
				}
				report(tabIdx, c, before, after);
			} else {
				proceedToDelta = true;
				int groupCount = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false).size();
				report(c, Severity.LOW, ReportActionType.INFO, before, after, groupCount, hasMultipleSameType?"Y":"N");
			}

			if (!proceedToDelta) {
				changesMade = false;
			}
		}
		return changesMade;
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

	private boolean inScope(Concept c) {
		/*if (!c.getConceptId().equals("396631001")) {
			return false;
		}*/

		//Are we in scope more generally?
		if (!super.inScope(c)) {
			return false;
		}

		//But also, we're looking for concepts that have more the one self grouped attribute
		boolean hasSelfGroupedAttribute = false;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)) {
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
	}

}
