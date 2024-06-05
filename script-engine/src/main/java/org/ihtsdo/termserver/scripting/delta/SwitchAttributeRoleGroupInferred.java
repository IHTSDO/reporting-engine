package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SwitchAttributeRoleGroupInferred extends DeltaGenerator {
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;

	private final int BatchSize = 99999;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		SwitchAttributeRoleGroupInferred delta = new SwitchAttributeRoleGroupInferred();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			delta.runStandAlone = true;
			delta.additionalReportColumns = "Action Detail";
			delta.newIdsRequired = false;
			delta.init(args);
			delta.loadProjectSnapshot(true);
			delta.postLoadInit();
			int lastBatchSize = delta.process();
			delta.createOutputArchive(false, lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		relTemplate = new RelationshipTemplate(gl.getConcept("272741003 |Laterality (attribute)|"), gl.getConcept("182353008 |Side|"));
		exclusions = new HashSet<>();
		super.postInit();
	}

	public int process() throws TermServerScriptException {
		int conceptsInThisBatch = 0;
		for (Concept c : determineConceptsToProcess()) {
				int changesMade = moveAttributeGroup(c);
				if (changesMade > 0) {
					outputRF2(c);
					conceptsInThisBatch++;
					if (conceptsInThisBatch >= BatchSize) {
						createOutputArchive(false, conceptsInThisBatch);
						gl.setAllComponentsClean();
						outputDirName = "output"; //Reset so we don't end up with _1_1_1
						initialiseOutputDirectory();
						initialiseFileHeaders();
						conceptsInThisBatch = 0;
					}
				}

		}
		return conceptsInThisBatch;
	}

	private List<Concept> determineConceptsToProcess() throws TermServerScriptException {
		List<Concept> conceptsToProcess = new ArrayList<>();
		for (Concept c : BODY_STRUCTURE.getDescendants(RF2Constants.NOT_SET)) {
			relTemplate.setCharacteristicType(CharacteristicType.INFERRED_RELATIONSHIP);
			for (Relationship r : c.getRelationships(relTemplate, ActiveState.ACTIVE)) {
				if (r.getGroupId() > 0) {
					conceptsToProcess.add(c);
				}
			}
		}
		return conceptsToProcess;
	}

	private int moveAttributeGroup(Concept c) throws TermServerScriptException {
		int change = NO_CHANGES_MADE;
		if (isExcluded(c)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			relTemplate.setCharacteristicType(CharacteristicType.INFERRED_RELATIONSHIP);
			for (Relationship r : c.getRelationships(relTemplate, ActiveState.ACTIVE)) {
				if (r.getGroupId() > UNGROUPED) {
					r.setGroupId(UNGROUPED);
					r.setDirty();
					change = CHANGE_MADE;
					report(c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, r, UNGROUPED);
				} else {
					report(c, Severity.MEDIUM, ReportActionType.INFO, "Relationship already ungrouped");
				}
			}
		}
		return change;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		for (String exclusionWord : exclusions) {
			if (fsn.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}
}
