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

	private static final Logger LOGGER = LoggerFactory.getLogger(ReassertPublishedComponentState.class);

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
	
	/*String[] componentsToProcess = new String[] {
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
	
	String[] componentsToProcess = new String[] { 
			"870683026",
			"870682020",
			"2485242028"
	};

	/*String[] componentsToProcess = new String[]{
			"85842356-607a-f4e8-61f4-24349243b309", "8b6307c0-ad41-6cca-5cfa-c2130d0bb747", "8d03e4cc-0ea6-4ecc-0fd7-79220203edb9",
			"903e032f-2c7f-2317-3e70-f06d9fd7196a", "909cee75-4639-3da5-efad-e4b8245703e1", "92dbab3c-6d1c-9498-e220-000186ab3166",
			"a7141064-9a4e-7508-b14f-cf2b9f1c6aed", "aad27e67-91c3-810c-c5d3-d0dd620fb0d9", "aba6ae6f-54a9-c13a-2257-f1862893c3f1",
			"b09a1c49-90be-4b54-59c5-300bb4e78d44", "b80cccbb-7a1a-5220-318b-e2c4e7e4d691", "b849d712-4749-8955-194f-414eb61f9352",
			"c6ad2db7-a192-bab7-ea26-47008848a829", "ca341156-4184-fe59-f1e7-24bf6865c626", "cc6e16ef-957d-921c-c9ee-dcdcd0546a55",
			"d6cd6325-8ca0-6c39-b3f9-498c014c9e53", "d7e58842-53c1-6770-6343-c7bcb99a2b2c", "f0dc5ebf-6342-09c9-869d-3588e0406583",
			"f2ea57f3-b0b2-e2f8-4944-c41509efe335", "fcff5514-6cfc-b468-7ab0-faf7d0c70fbc", "fef14894-1595-fcc7-e316-bd16fb2ed052",
			"ff87106c-728b-2c54-367e-1bd9275c3991", "02cf7245-5443-bb9a-512a-d4fbe91f1dee", "0a40c325-8439-a5f5-4e3b-288cf0f010be",
			"0c60fb73-1c0a-7810-2536-4d9db65745fa", "0d27954b-566c-3f4e-9e71-f8f911ba5ccc", "201eb513-1822-ccfa-ab70-d498503b4068",
			"2adbd4c2-c1c7-1455-8e36-8a11269ab1ed", "2e4faf1b-da02-ee36-ba5c-e2b85b159eb1", "33d38ab5-b3b9-61a2-9533-68fe2da7986e",
			"3ea3b567-dcee-ee39-215c-a6bbc116b182", "5354d302-ca90-9dc2-4e63-cd428f877f22", "55ac661e-bb08-764c-50da-72919e8f4d99",
			"591ff3c7-03d7-6c89-d158-7d3faaabddc0", "649eee76-7d7f-6591-715b-dc5e8ef74691", "6528d96d-84f7-c4cf-20a9-f8356282d431",
			"6cf085a5-d67a-1f18-21d3-d15ba24fbcc8"
	};*/

	String[] componentsToProcess = new String[] {
			"3c0b3629-77d3-5027-b3cb-0ef4ab5d6636",
			"856d0be8-d966-5999-959c-b42c85bccd4e",
			"80bdda99-2d35-4f86-9d92-772d58f96d19"
	};

	String[] componentsToProcess = new String[] {
			"3c0b3629-77d3-5027-b3cb-0ef4ab5d6636",
			"856d0be8-d966-5999-959c-b42c85bccd4e",
			"80bdda99-2d35-4f86-9d92-772d58f96d19"
	};

	String[] componentsToProcess = new String[] {
			"3c0b3629-77d3-5027-b3cb-0ef4ab5d6636",
			"856d0be8-d966-5999-959c-b42c85bccd4e",
			"80bdda99-2d35-4f86-9d92-772d58f96d19"
	};

	String[] componentsToProcess = new String[] {
						/*"903745025",
						"2047987023",
						"750938028",
						"653945028",
						"653946027",
						"872235028",
						"1814546022",
						"887612024",
						"887613025",
						"673081029"
						"2041987026",
						"861386023"};

	String[] componentsToProcess = new String[] {
						"3333dcf8-332d-b99b-7d59-6c4d34b63cf2",
						"75cb4cac-ac05-fae4-1b35-d251a90198ad",
						"d140b306-ecea-2518-852e-a9e4203650b9",
						"e2b9b0ce-4444-9aab-7b95-d9d19cd15ec2"};

	String[] componentsToProcess = new String[] {
			"895479026",
			"895478023",
			"895477029"};*/
	String[] componentsToProcess = new String[] {
			"c9d1c9ec-b15b-4a23-b986-b1e11cf9e41a"};

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
	
	public void init (String[] args) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
		super.init(args);
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
