package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1661 Need to reassert various components back to their previously published state,
 * but restricted to those exact components
 */
public class ReassertPublishedComponentState extends DeltaGenerator {
	
	String processMe = "fa645771-ed9c-5e3c-8247-4da1b5186826,"+
			"c0683280-a7a2-579b-9cbe-8bbc340065e6,"+
			"6a9c841d-2251-5655-97b4-5fa71b81d166,"+
			"f46ae5c5-8fec-530c-a66c-3ee216324646,"+
			"1b62ad94-dfd6-5b4d-af38-b3ae5eb82ff3,"+
			"68393826-0de6-5686-9ca3-7e4f7b4e9aa8,"+
			"db571651-b986-59c5-b4b7-ffa7592630ae,"+
			"45f12bb5-e48f-5ae3-a9b1-36030f94e2d5,"+
			"c6e73b88-29da-565f-81e5-4297d9e45ebb,"+
			"703ef9b1-1570-566d-b527-8acf4aa5d7ce,"+
			"dde010ae-4694-5ace-b106-e2493185e503,"+
			"f370fa9c-7c89-575d-a85c-d3c619e65b37,"+
			"db142643-808d-590d-8568-bb53ab8a1696,"+
			"b87b8ddd-8958-5d21-b92f-ae168323518f,"+
			"ca7b51c4-53e2-5ced-859f-91743ebeba66,"+
			"0e26d5fa-276a-5b8f-afcf-873438eb31d5,"+
			"d74ffe70-485c-5da8-ad99-0dbd19d76767,"+
			"ee781cc3-e511-523e-93ae-0ead62d293ff,"+
			"207c9b7c-b5fb-5e0f-82ac-6bc5667040ae,"+
			"7669bff9-099d-53e0-bc75-219bbf86da61,"+
			"7103dd74-0e4e-5f95-aae2-00c079b2c096,"+
			"5dcd2f22-febd-5a54-8dcb-25f846c3b61d,"+
			"90f1d8c6-3a4c-52f9-9df8-9d59e42b03d8,"+
			"f7eb1664-9599-58f8-a41b-c890315cc751,"+
			"7d9ea2f6-0392-52d3-afd9-62bf6c1b27c3,"+
			"d093c7ce-0fd4-53d7-85fb-ae2efb2084ae,"+
			"e375f828-30d7-5a34-a933-e2cddb2ccb91,"+
			"898d7dd2-fae5-58ca-b386-4d24f5d0dcaf";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReassertPublishedComponentState delta = new ReassertPublishedComponentState();
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
			if (delta.descIdGenerator != null) {
				info(delta.descIdGenerator.finish());
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
				"Id, FSN, SemTag, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void process() throws TermServerScriptException {
		Set<String> componentsToProcess = new HashSet<>(Arrays.asList(processMe.split(",")));
		for (String componentId : componentsToProcess) {
			Component component = gl.getComponent(componentId);
			Concept owningConcept = gl.getComponentOwner(component.getId());
			report (owningConcept, component);
			component.setDirty();
			outputRF2(owningConcept, true);  //Will only output dirty fields.
			component.setClean();  //Revert this in case we output other components for this concept.
			//Don't want to output this one multiple times.
		}
	}

}