package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to inactivate redundant IS A relationships where a more specific parent exists
 */
public class InactivateRedundantStatedRelationshipsViaDelta extends DeltaGenerator implements ScriptConstants {

	public static void main(String[] args) throws TermServerScriptException {
		InactivateRedundantStatedRelationshipsViaDelta delta = new InactivateRedundantStatedRelationshipsViaDelta();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(true);  //Just FSN, not working with all descriptions here
			//We won't include the project export in our timings
			delta.startTimer();
			delta.process();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		print ("Processing concepts to look for redundant IS A relationships");
		for (Concept concept : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (concept.isActiveSafely()) {
				//We're working with concepts which have multiple stated parents.
				if (concept.getParents(CharacteristicType.STATED_RELATIONSHIP).size() > 1) {
					removeRedundantStatedParents(concept);
				}
			}
			if (concept.isModified()) {
				incrementSummaryInformation("Concepts modified");
				if (concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					report(concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined");
				}
				outputRF2(concept);  //Will only output dirty fields.
			}
		}
	}

	private void removeRedundantStatedParents(Concept concept) throws TermServerScriptException {
		Set<Relationship> activeISAs = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		for (Relationship moreSpecificISA : activeISAs) {
			//Do we have another IS A that is parent of this relationship?  Inactivate it if so.
			for (Relationship lessSpecificISA : activeISAs) {
				if (moreSpecificISA.equals(lessSpecificISA) || !lessSpecificISA.isActive()) {
					continue; //Skip self or already processed
				}
				Set<Concept> ancestors = moreSpecificISA.getTarget().getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, false);
				if (ancestors.contains(lessSpecificISA.getTarget())) {
					//Are we inactivating an unpublished relationship?   Must warn user to delete if so.
					if (lessSpecificISA.getEffectiveTime() == null || lessSpecificISA.getEffectiveTime().isEmpty() ||  Long.parseLong(lessSpecificISA.getEffectiveTime()) > 20170131L) {
						report(concept, concept.getFSNDescription(), Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Inactivating new relationship - should be deleted");
					}
					lessSpecificISA.setActive(false);
					concept.setModified();
					String msg = "Inactivated " + lessSpecificISA + " in favour of more specific " + moreSpecificISA;
					report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_INACTIVATED, msg);
				}
			}
		}
	}

}
