package org.ihtsdo.termserver.scripting.delta.ms;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MSSP-1598 where we've moved a component from one module to another
 * but then inactivated it (or it was already inactive) then we have validation
 * that ensures "New inactive states follow active states"
 * 
 * Detect where a module change has been made but a concept remains inactive,
 * and revert it back to its original module
 */
public class RevertNewInactiveStateModuleJump extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(RevertNewInactiveStateModuleJump.class);

	public static void main(String[] args) throws TermServerScriptException {
		RevertNewInactiveStateModuleJump delta = new RevertNewInactiveStateModuleJump();
		delta.targetModuleId = "15561000146104";
		delta.standardExecution(args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleaseFlag(true);
		gl.setRecordPreviousState(true);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Action, ComponentType, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(GFOLDER_MS, tabNames, columnHeadings);
	}

	@Override
	public void process() throws TermServerScriptException {
		int processedCount = 0;
		for (Concept concept : gl.getAllConcepts()) {
			if (concept.getId().equals("15551000146102")) {
				continue;
			}
			if (++processedCount%100000==0) {
				LOGGER.debug("Processed: {}", processedCount);
			}
			//Work through all the components for this concept
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				if (!c.isActiveSafely() && sourceModuleIds.contains(c.getModuleId())
						&& c.getIssues(" ").contains(targetModuleId)) {
					//Only need to worry if it was also previously inactive
					String[] previousState = c.getIssuesArray();
					if (previousState[0].equals("0")) {
						c.setModuleId(targetModuleId);
						c.setDirty();
						concept.setModified();
						if (!c.isReleasedSafely()) {
							report(concept, ReportActionType.VALIDATION_CHECK,"Check inactive unpublished component", c);
						}
						//For a description, make sure any langrefset or associations follow
						if (c instanceof Description d) {
							switchDescriptionComponentsIfRequired(concept, d);
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
