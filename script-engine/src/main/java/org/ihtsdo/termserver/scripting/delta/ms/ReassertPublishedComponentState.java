package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;

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
	
	//String processMe = "fa645771-ed9c-5e3c-8247-4da1b5186826,"+
	String[] componentsToProcess = new String[] {
			"071f31c9-090d-5d88-a7ca-3566faa1c407",
			"2fb4e159-a683-5db7-b65b-fa601862db45",
			"7f430d74-88b3-5a02-8a6f-d86bce1ce4dd",
			"7d3ff964-71b8-5ff2-bbf3-e30a5e51d008",
			"2a5acf88-a252-50ec-9f5f-0ce86b6316b4",
			"2bbf69a7-3cce-5fbb-aa93-7397da89fc0e",
			"ad436683-e799-57f6-bcd7-4c79f37483cb",
			"080c5787-ad1d-5ad1-b412-10b95c066081",
			"f741e108-41a4-5fa4-b937-f783b73a7b8e",
			"04a0262f-3b62-5201-bf93-e65c69c510d3",
			"bda60da0-05ff-55a5-8bde-3b9777e931c3",
			"f8b590bc-6640-5f7b-9ed4-9ebcc4341ec7",
			"2fcd2bb3-6375-59b9-8f09-540567f7c801",
			"ed21d63a-f53f-5eef-9a55-9769c7bba742",
			"cb49eb2b-524c-5850-b49d-74f1f7973ab6",
			"c343bb35-f10d-5bc8-9e99-e9ab50dc0c6b",
			"747fe056-62ba-5049-8c0e-78d4e137fd75",
			"64bb6fd8-2b1a-5aa3-ae58-e6aed47ab583",
			"dae53b10-3f2f-50ea-b7b2-f852c4b16b28",
			"b1c0f1a0-7939-5053-881b-ba3db79d898b",
			"4399b41e-3b62-59f1-8bcd-4370d77ff5f6",
			"dcebf673-81d6-41d8-a7a4-958699a62cd1",
			"3918ac29-553e-5bb3-bec2-221af4f07fa8",
			"a7ae6663-1890-4f47-b960-edf5330350d9",
			"04dbe867-a083-5d37-9b1b-927d7db2b6e1",
			"339f8001-0f77-4b0f-8662-1192997829b2",
			"1426f4e7-83cb-56c6-8019-964e3e90ae23",
			"4993b94b-da94-5a33-8673-df07688968fe",
			"8e671263-f8e0-4ddf-a5f8-3d1b3e31b120",
			"fa56152a-7dc7-53e1-9b2f-796ae31217e0",
			"723cf1e6-ca6b-5367-9dc3-2acb0e1b7432",
			"332164bb-d9b8-5ab4-a2db-1af25e3c9874",
			"4b38ec30-4cbd-5a40-ae3a-307e93f07c37",
			"b765fd4e-9044-5797-a139-470b407789f2",
			"7bd5e36b-1dcd-5055-acad-f42776466c45",
			"22ad5fa3-56af-4b70-ac5b-54553d2e45dd",
			"7e975477-cf55-541a-be3c-8e7c1d8b173c",
			"961c5ee8-fd88-53b1-86f2-886dafbcc071",
			"8663c346-0288-5ae3-be41-7b71f727b349",
			"7eaa68db-0ddc-52e9-a08a-81d117a672b8",
			"158eb50e-6688-5dbd-90b3-b37578f720ec",
			"5c377aba-6770-55b7-bba1-524312e4b9ef",
			"2bbf4cca-5108-5da4-a35a-8d4432e9b269",
			"f6b83ed8-c6ab-54cd-923e-c94e87352cab",
			"91ad8e20-6154-5542-bd00-8c34cdf620a8",
			"4fa27021-8eca-4501-bf63-435a21cfc2c8",
			"c5ef32eb-0440-5d55-a064-5d00114e249e",
			"286ae396-3c07-56c6-835c-51d633fdbca6",
			"fa59acd2-0469-52d2-9c95-74e8ec6589f5",
			"9cc3f57e-17b7-4ee2-8d2c-06dfdb11cbbc",
			"fddab52f-d8fa-5150-9b58-cfb9f8ca7ad6",
			"a25a7f1e-d272-5488-9752-9fef718c54dd",
			"33f5a51d-75cd-5184-945e-88c513d32120",
			"81407459-9686-526a-a863-36f717b8f57d",
			"dd400ac2-aced-5ff2-8973-ea0d9595cad9",
			"460b6d36-ca47-5734-a32f-ffc0b80613a5",
			"b3c5e772-7258-534e-af12-fdaa185d0f94",
			"5b008d71-4a6c-5a07-b2f5-1441e893f283",
			"0f573b2e-5175-5bdd-8505-a1f88e307f2c",
			"c16094fe-8ef9-5c53-97cb-c1446ec4b8f9",
			"0f5795fb-1c62-505c-8994-fb5bd7877af5",
			"d61a9b5b-393e-5da8-800e-d5f555a37727"
	};
	
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
		//Set<String> componentsToProcess = new HashSet<>(Arrays.asList(processMe.split(",")));
		for (String componentId : componentsToProcess) {
			Component component = gl.getComponent(componentId);
			if (component == null) {
				report((Concept)null, componentId, "Not found in " + project.getKey());
			} else {
				Concept owningConcept = gl.getComponentOwner(component.getId());
				report (owningConcept, component);
				component.setDirty();
				outputRF2(owningConcept, true);  //Will only output dirty fields.
				component.setClean();  //Revert this in case we output other components for this concept.
				//Don't want to output this one multiple times.
			}
		}
	}

}