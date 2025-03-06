package org.ihtsdo.termserver.scripting.delta;

import java.io.File;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * MSSP-1661 Need to reassert various components back to their previously published state,
 * but restricted to those exact components
 */

public class ReassertPublishedComponentState extends DeltaGenerator {

	String[] componentsToProcess = new String[] {
			"663114025","663113020","734078021","776663023"};

	public static void main(String[] args) throws TermServerScriptException {
		ReassertPublishedComponentState delta = new ReassertPublishedComponentState();
		try {
			delta.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			//It might be that we need component from an upgraded international edition
			//so we might have very bad integrity.  Doesn't matter in this case, we just
			//need the rows from the RF2
			delta.getArchiveManager().setPopulateHierarchyDepth(false);
			delta.getArchiveManager().setRunIntegrityChecks(false);
			delta.loadProjectSnapshot(false);
			delta.postInit(GFOLDER_MS);
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			if (!dryRun) {
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
		}
	}

	@Override
	public void init (String[] args) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		super.init(args);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, ModuleId, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		
		super.postInit(googleFolder, tabNames, columnHeadings, false);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (String componentId : componentsToProcess) {
			Component component = gl.getComponent(componentId);
			if (component == null) {
				report((Concept)null, componentId, "Not found in " + project.getKey());
			} else {
				Concept owningConcept = gl.getComponentOwner(component.getId());
				report(owningConcept, component.getModuleId(), component);
				component.setDirty();
				outputRF2(owningConcept, true);  //Will only output dirty fields.
				component.setClean();  //Revert this in case we output other components for this concept.
				//Don't want to output this one multiple times.
			}
		}
	}

}
