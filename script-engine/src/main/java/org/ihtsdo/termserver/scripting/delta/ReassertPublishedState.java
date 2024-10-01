package org.ihtsdo.termserver.scripting.delta;

import java.io.File;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-8273 Need to reassert various components back to their previously published state
 * NOTE: Run this script against a release archive
 */

public class ReassertPublishedState extends DeltaGenerator {

	static {
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
	}

	String processMe = "372440000, 384786009";
	String intReleaseBranch="MAIN/2022-01-31";
	
	public static void main(String[] args) throws TermServerScriptException {
		ReassertPublishedState delta = new ReassertPublishedState();
		try {
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
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
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void process() throws TermServerScriptException {
		for (String sctId : processMe.split(",")) {
			Concept c = gl.getConcept(sctId.trim());
			c.setDirty();
			for (Component component : SnomedUtils.getAllComponents(c)) {
				report (c, component);
				component.setDirty();
			}
			outputRF2(c, true);  //Will only output dirty fields.
		}
	}

}
