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
 * Class to inactivate duplicated active inactivation indicators
 * INFRA-1232
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateDuplicateInactivationIndicators extends DeltaGenerator implements ScriptConstants {

	private static Logger LOGGER = LoggerFactory.getLogger(InactivateDuplicateInactivationIndicators.class);

	public static String SCTID_ERRONEOUS = "900000000000485001";
	public static String SCTID_DUPLICATE = "900000000000482003";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivateDuplicateInactivationIndicators delta = new InactivateDuplicateInactivationIndicators();
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

	private void process() throws TermServerScriptException {
		print ("Processing concepts to look for redundant IS A relationships");
		for (Concept concept : GraphLoader.getGraphLoader().getAllConcepts()) {
			//We're working with inactive concepts
			if (!concept.isActive()) {
				fixInactivationIndicators(concept);
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
	
	private void fixInactivationIndicators(Concept concept) throws TermServerScriptException {
		//If we have both INT and Extension active inactivation indicators, inactivate all the extension indicators.
		//If we have no INT but multiple Extension inactive indicators, list as a critical validation error for the moment.
		int coreIndicators = 0;
		int extensionIndicators = 0;
		
		List<InactivationIndicatorEntry> activeIndicators = concept.getInactivationIndicatorEntries(ActiveState.ACTIVE);
		
		for (InactivationIndicatorEntry i : activeIndicators) {
			if (i.getModuleId().equals(SCTID_CORE_MODULE)) {
				coreIndicators++;
			} else extensionIndicators ++;
		}	
			
		if (coreIndicators > 1) {
			report(concept, concept.getFSNDescription(), Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, coreIndicators + " active inactivation indicators in core module.  Skipping.");
		} else if (coreIndicators == 1) {
			for (InactivationIndicatorEntry i : activeIndicators) {
				if (!i.getModuleId().equals(SCTID_CORE_MODULE)) {
					i.setActive(false);
					concept.setModified();
					String msg = "Inactivated " + i;
					report(concept, concept.getFSNDescription(), Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, msg);
				} 
			}	
		} else if (extensionIndicators > 1) {
			//Currently we're only seeing "Erroneous" and "Duplicate" together.
			//Check that this is still the case, and inactive "Erroneous"
			InactivationIndicatorEntry erroneous = null;
			InactivationIndicatorEntry duplicate = null;
			for (InactivationIndicatorEntry i : activeIndicators) {
				if (i.getInactivationReasonId().equals(SCTID_ERRONEOUS)) {
					//Is this our first one?
					if (erroneous == null) {
						erroneous = i;
					} else {
						report(concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Multiple active Erroneous indicators encountered");
					}
				} else if (i.getInactivationReasonId().equals(SCTID_DUPLICATE)) {
					//Is this our first one?
					if (duplicate == null) {
						duplicate = i;
					} else {
						report(concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Multiple active Duplicate indicators encountered");
					} 
				} else {
					report(concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Multiple active indicators encountered: " + i.getInactivationReasonId());
				}
			}
			//Did we just get one of each?
			if (erroneous != null && duplicate != null) {
				erroneous.setActive(false);
				concept.setModified();
				String msg = "Inactivated " + erroneous;
				report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.REFSET_MEMBER_REMOVED, msg);
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}
