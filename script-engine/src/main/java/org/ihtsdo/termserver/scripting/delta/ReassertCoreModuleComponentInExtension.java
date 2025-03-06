package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.io.File;
import java.util.List;

/**
 * MSSP-3619 Where some misbehaving process has resulted in a component being modified in an extension,
 * but using a core module id, revert it back to its previous published core state, thus restoring its effective time
 * and removing it from the extension Delta.
 * This is a fix for assertion failures such as "All the changes in the release are in the expected set of modules"
 * attributevaluerefset ::id= 2836ce1c-13c7-4f16-bf10-785e86087602 ::module id: 900000000000207008 was made in the release but not in the expected set of modules
 */
public class ReassertCoreModuleComponentInExtension extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		ReassertCoreModuleComponentInExtension delta = new ReassertCoreModuleComponentInExtension();
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
			GraphLoader.getGraphLoader().setRecordPreviousState(true);
			delta.loadProjectSnapshot(false);
			delta.postInit(GFOLDER_ADHOC_UPDATES);
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
				"Id, FSN, SemTag, ModuleId, Component Reasserted, Problem State"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		
		super.postInit(GFOLDER_MS, tabNames, columnHeadings);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Component c : identifyComponentsToProcess()) {
			Concept owningConcept = gl.getComponentOwner(c.getId());
			String problemState = c.toString();
			if (c.hasPreviousStateDataRecorded()) {
				c.revertToPreviousState();
				report(owningConcept, c.getModuleId(), c, problemState);
				c.setDirty();
				outputRF2(owningConcept, true);  //Will only output dirty fields.
				c.setClean();  //Revert this in case we output other components for this concept.
				//Don't want to output this one multiple times.
			} else {
				report(owningConcept, c.getModuleId(), "No previous state, consider deleting", problemState);
			}
		}
	}

	private List<Component> identifyComponentsToProcess() {
		return gl.getAllComponents().stream()
				.filter(c -> c.getModuleId().equals(SCTID_CORE_MODULE)
						&& StringUtils.isEmpty(c.getEffectiveTime()))
				.toList();
	}


}
