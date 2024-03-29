package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateDuplicateRelationships extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateDuplicateRelationships.class);

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
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
			if (delta.descIdGenerator != null) {
				LOGGER.info(delta.descIdGenerator.finish());
			}
		}
	}

	private void process() throws TermServerScriptException {
		
		for (Concept concept : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (concept.isActive()) {
				for (Relationship rExtension : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					//Where the relationship is not-core, look for a duplicate core relationship 
					if (!rExtension.getModuleId().equals(SCTID_CORE_MODULE)) {
						for (Relationship rCore : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
							if (rExtension.equals(rCore) && rCore.getModuleId().equals(SCTID_CORE_MODULE)) {
								rExtension.setActive(false);
								rExtension.setEffectiveTime(null);
								concept.setModified();
								String msg = "Inactivated " + rExtension + " + in module " + rExtension.getModuleId();
								report (concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_INACTIVATED, msg);
							}
						}
					}
				}
			}
			if (concept.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(concept);  //Will only output dirty fields.
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}
