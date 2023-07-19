package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1661 Need to reassert various components back to their previously published state,
 * but restricted to those exact components
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReassertPublishedComponentState extends DeltaGenerator {

	private static Logger LOGGER = LoggerFactory.getLogger(ReassertPublishedComponentState.class);

	/*String[] componentsToProcess = new String[] {
		"5169695010",
		"5090569016",
		"e1d8fe7d-5caa-4a89-86f9-418ccf4700dc",
		"42fd1dd9-5341-46ab-9be9-601dc4b47e78",
		"07abc513-d04c-4447-ac8b-ad40923e1b38",
		"6ec2a92d-a4ea-4c28-8fa1-540239eb543b",
		"e50189c8-6ed4-4a79-be1b-6fb61112eb20",
		"8aa56630-0ba6-47a8-a7e2-6219d9d69e88",
		"a99d3d06-6633-4065-9540-fc85c4bfc719",
		"e2495aeb-968f-4c9d-802e-a0f5d08303ac",
		"63d92d0d-df7d-42b6-9d8f-0747c2f03ebf",
		"9df1e144-cc69-447d-b133-9c2a7e731e20",
		"fdce7c61-3e0b-4f04-b6f6-8d8bafb678e5",
		"f9314dd1-aec0-430d-bf16-c442d854be45"
	};
	
	String[] componentsToProcess = new String[] {
			"105221000220106",
			"233321000220112",
			"233331000220110"
	};
	
	String[] componentsToProcess = new String[] {
			"1e684afa-9319-4b24-9489-40caef554e13"
	};*/
	
	/*String[] componentsToProcess = new String[] {
			"5146860018",
			"36820a1d-ed25-48ca-90d0-bda314dedf8b",
			"6b2bc82b-fac5-4094-a910-4b5db67ed035"
	};*/
	
	String[] componentsToProcess = new String[] {
			"c20605c3-af44-43d3-884a-128bba695926",
			"a0ebdaaf-8ccf-4a5e-a6a8-e0e02b5ca0e1",
			"30ca1516-1c8a-4a76-902e-aaf03a4beb4a",
			"1d44c91b-95fa-4b1b-ba11-a0100e6bea1f",
			"41fce30f-1658-4b2e-b56a-1d803ef462a7",
			"57cace7e-011a-40ff-9a78-f8369b17b911",
			"10605c21-de9f-47d9-a179-3f3e69e7c5d3",
			"ed5098ef-2b7c-4047-ac62-e7aafa0f05de",
			"48b1a539-53d2-4862-a3c1-609e6e8793a9",
			"b0a6e733-04e4-4ce1-aca9-5ff605a4398a"
	};
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReassertPublishedComponentState delta = new ReassertPublishedComponentState();
		try {
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			//It might be that we need component from an upgraded international edition
			//so we might have very bad integrity.  Doesn't matter in this case, we just
			//need the rows from the RF2
			delta.getArchiveManager(true).setPopulateHierarchyDepth(false);
			delta.getArchiveManager(true).setRunIntegrityChecks(false);
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
				"Id, FSN, SemTag, ModuleId, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void process() throws TermServerScriptException {
		//Set<String> componentsToProcess = new HashSet<>(Arrays.asList(processMe.split(",")));
		for (String componentId : componentsToProcess) {
			Component component = gl.getComponent(componentId);
			if (component == null) {
				report((Concept)null, componentId, "Not found in " + project.getKey());
			} else {
				Concept owningConcept = gl.getComponentOwner(component.getId());
				report (owningConcept, component.getModuleId(), component);
				component.setDirty();
				outputRF2(owningConcept, true);  //Will only output dirty fields.
				component.setClean();  //Revert this in case we output other components for this concept.
				//Don't want to output this one multiple times.
			}
		}
	}

}
