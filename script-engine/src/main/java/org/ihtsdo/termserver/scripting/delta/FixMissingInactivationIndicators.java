package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
	Where a concept is inactive, it's active descriptions should have the inactivation indicator 
	900000000000495008 |Concept non-current (foundation metadata concept)| applied against them.
	See INFRA-1407 and ISRS-225
	
	This class replaces the "fix" version so that we are sure of having new UUIDs for these new
	indicators
	
	MAINT-489
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixMissingInactivationIndicators extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(FixMissingInactivationIndicators.class);

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		FixMissingInactivationIndicators delta = new FixMissingInactivationIndicators();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions
			delta.allowMissingExpectedModules = true;
			delta.init(args);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			delta.process();
			if (!dryRun) {
				delta.flushFiles(false); //Need to flush files before zipping
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
		}
	}

	private void process() throws ValidationFailure, TermServerScriptException {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getInactivationIndicator() == null && inScope(d)) {
						InactivationIndicatorEntry i = addConceptNotCurrentInactivationIndicator(c, d);
						report (c, d, i);
						outputRF2(d);
						incrementSummaryInformation("Inactivation indicators added");
					}
				}
			}
		}
	}

	public InactivationIndicatorEntry addConceptNotCurrentInactivationIndicator(Concept c, Description d) throws TermServerScriptException, ValidationFailure {
		InactivationIndicatorEntry cnc = InactivationIndicatorEntry.withDefaults(d, SCTID_INACT_CONCEPT_NON_CURRENT);
		d.addInactivationIndicator(cnc);
		return cnc;
	}

}
