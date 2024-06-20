package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.RF2Constants.ReportActionType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1598 where we've moved a component from one module to another
 * but then inactivated it (or it was already inactive) then we have validation
 * that ensures "New inactive states follow active states"
 * 
 * Detect where a module change has been made but a concept remains inactive,
 * and revert it back to its original module
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RevertNewInactiveStateModuleJump extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(RevertNewInactiveStateModuleJump.class);

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RevertNewInactiveStateModuleJump delta = new RevertNewInactiveStateModuleJump();
		try {
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.targetModuleId = "15561000146104";
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false;
			delta.gl.setRecordPreviousState(true);
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);
			
			if (!dryRun) {
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				LOGGER.info(delta.descIdGenerator.finish());
			}
		}
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Action, ComponentType, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void process() throws TermServerScriptException {
		int processedCount = 0;
		for (Concept concept : gl.getAllConcepts()) {
			if (concept.getId().equals("15551000146102")) {
				continue;
			}
			if (++processedCount%100000==0) {
				LOGGER.debug ("Processed: " + processedCount);
			}
			//Work through all the components for this concept
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				if (!c.isActive() && sourceModuleIds.contains(c.getModuleId())
						&& c.getIssues().contains(targetModuleId)) {
					//Only need to worry if it was also previously inactive
					String[] previousState = c.getIssues().split(",");
					if (previousState[0].equals("0")) {
						c.setModuleId(targetModuleId);
						c.setDirty();
						concept.setModified();
						if (!c.isReleased()) {
							report(concept, ReportActionType.VALIDATION_CHECK,"Check inactive unpublished component", c);
						}
						//For a description, make sure any langrefset or associations follow
						if (c instanceof Description) {
							switchDescriptionComponentsIfRequired(concept, (Description)c);
						}
						report(concept, ReportActionType.MODULE_CHANGE_MADE, c.getComponentType(), c);
					}
				}
			}
			
			if (concept.isModified()) {
				outputRF2(concept, true);  //Will only output dirty fields.
			}
		}
	}

	private void switchDescriptionComponentsIfRequired(Concept concept, Description d) throws TermServerScriptException {
		for (Component c : SnomedUtils.getAllComponents(d)) {
			if (sourceModuleIds.contains(c.getModuleId())) {
				c.setModuleId(targetModuleId);
				c.setDirty();
				report(concept, ReportActionType.MODULE_CHANGE_MADE, c.getComponentType(), c);
			}
		}
	}

}
