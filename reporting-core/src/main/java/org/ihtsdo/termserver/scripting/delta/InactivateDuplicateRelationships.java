package org.ihtsdo.termserver.scripting.delta;

import java.io.File;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.client.TermServerClient;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to replace relationships with alternatives
 * Example TS Task: MAIN/2017-01-31/SNOMEDCT-US/USTEST/USTEST-6002
 * INFRA-1232
 */

public class InactivateDuplicateRelationships extends DeltaGenerator implements ScriptConstants {

	public static void main(String[] args) throws TermServerScriptException {
		InactivateDuplicateRelationships delta = new InactivateDuplicateRelationships();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			TermServerClient.supportsIncludeUnpublished = false;   //This code not yet available in MS
			delta.tsRoot="MAIN/2017-01-31/SNOMEDCT-US/";
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
		
		for (Concept concept : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (concept.isActiveSafely()) {
				for (Relationship rExtension : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					processRelationship(concept, rExtension);
				}
			}
			if (concept.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(concept);  //Will only output dirty fields.
			}
		}
	}

	private void processRelationship(Concept concept, Relationship rExtension) throws TermServerScriptException {
		//Where the relationship is not-core, look for a duplicate core relationship
		if (!rExtension.getModuleId().equals(SCTID_CORE_MODULE)) {
			for (Relationship rCore : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (rExtension.equals(rCore) && rCore.getModuleId().equals(SCTID_CORE_MODULE)) {
					rExtension.setActive(false);
					rExtension.setEffectiveTime(null);
					concept.setModified();
					String msg = "Inactivated " + rExtension + " + in module " + rExtension.getModuleId();
					report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_INACTIVATED, msg);
				}
			}
		}
	}

}
