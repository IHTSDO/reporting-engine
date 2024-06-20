package org.ihtsdo.termserver.scripting.delta.oneOffs;

import java.io.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ISRS1400_SwitchModuleOfNewCNCIndicators extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ISRS1400_SwitchModuleOfNewCNCIndicators.class);

	public static String SCTID_CF_MOD = "11000241103";   //Common French Module
	public static String SCTID_CH_MOD = "2011000195101"; //Swiss Module
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ISRS1400_SwitchModuleOfNewCNCIndicators delta = new ISRS1400_SwitchModuleOfNewCNCIndicators();
		try {
			delta.targetModuleId = SCTID_CH_MOD;
			delta.runStandAlone = true;
			//delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; 
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				LOGGER.info(delta.descIdGenerator.finish());
			}
		}
	}
	
	public void process() throws TermServerScriptException, IOException {
		int conceptsProcessed = 0;
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (conceptsProcessed++%10000==0) {
				LOGGER.info("Concepts processed: " + (conceptsProcessed-1));
				getRF2Manager().flushFiles(false);
			}
			processConcept(c);
		}
		getRF2Manager().flushFiles(false);
	}
	
	private void processConcept(Concept c) throws TermServerScriptException {
		/*if (c.getId().equals("138875005")) {
			debug("here");
		}*/
		
		for (Description d : c.getDescriptions("fr", ActiveState.BOTH)) {
			//Look for CNC indicators that have no effective time, and are in the CF module
			for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
				if (SnomedUtils.isEmpty(i.getEffectiveTime()) && i.getModuleId().contentEquals(SCTID_CF_MOD)) {
					i.setModuleId(SCTID_CH_MOD);
					i.setDirty();
					c.setModified();
					report(c, Severity.LOW, ReportActionType.MODULE_CHANGE_MADE, d, i);
					countIssue(c);
				}
			}
		}
		
		if (c.isModified()) {
			incrementSummaryInformation("Concepts modified");
			outputRF2(c, true);  //Will only output dirty fields.
		}
	}
}
