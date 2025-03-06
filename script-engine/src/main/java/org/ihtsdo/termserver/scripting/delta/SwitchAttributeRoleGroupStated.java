package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;

import java.util.*;

public class SwitchAttributeRoleGroupStated extends DeltaGenerator {
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;

	private final int BatchSize = 99999;

	private int lastBatchSize = 0;
	
	public static void main(String[] args) throws TermServerScriptException {
		SwitchAttributeRoleGroupStated delta = new SwitchAttributeRoleGroupStated();
		try {
			delta.runStandAlone = true;
			delta.additionalReportColumns = "Action Detail";
			delta.newIdsRequired = false;
			delta.init(args);
			delta.loadProjectSnapshot(true);
			delta.postLoadInit();
			delta.process();
			delta.createOutputArchive(false, delta.lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//INFRA-12889
		subsetECL = "(^ 723264001) MINUS ( << 423857001 |Structure of half of body lateral to midsagittal plane (body structure)| MINUS ( * : 272741003 |Laterality (ttribute)| = ( 7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier alue)| )))";
		relTemplate = new RelationshipTemplate(gl.getConcept("272741003 |Laterality (attribute)|"), gl.getConcept("182353008 |Side|"));

		exclusions = new HashSet<>();
		super.postInit(GFOLDER_ADHOC_UPDATES);
	}

	@Override
	public void process() throws TermServerScriptException {
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
		lastBatchSize = conceptsInThisBatch;
	}

	private List<Concept> determineConceptsToProcess() throws TermServerScriptException {
		List<Concept> conceptsToProcess = new ArrayList<>();
		//Odd thing, we're going to try finding concepts that have a laterality=side group in the stated
		//view, and then when it's in a group in the inferred view, we're going to either move it,
		//or just reassert it as ungrouped
		for (Concept c : BODY_STRUCTURE.getDescendants(RF2Constants.NOT_SET)) {
			relTemplate.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			if (c.getRelationships(relTemplate, ActiveState.ACTIVE).size() > 0) {
				relTemplate.setCharacteristicType(CharacteristicType.INFERRED_RELATIONSHIP);
				for (Relationship r : c.getRelationships(relTemplate, ActiveState.ACTIVE)) {
					if (r.getGroupId() > 0) {
						conceptsToProcess.add(c);
					}
				}
			}
		}
		return conceptsToProcess;
	}

	private int moveAttributeGroup(Concept c) throws TermServerScriptException {
		if (isExcluded(c)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			relTemplate.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			for (Relationship r : c.getRelationships(relTemplate, ActiveState.ACTIVE)) {
				if (r.getGroupId() > UNGROUPED) {
					r.setGroupId(UNGROUPED);
					r.setDirty();
					report(c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, r, UNGROUPED);
				} else {
					report(c, Severity.MEDIUM, ReportActionType.INFO, "Relationship already ungrouped");
					r.setDirty();  //We will re-assert this relationship in the OWL file
				}
				return CHANGE_MADE;
			}
		}
		return NO_CHANGES_MADE;
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
