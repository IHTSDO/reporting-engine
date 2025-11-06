package org.ihtsdo.termserver.scripting.fixes.metadata;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.Iterables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertMetadata extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(UpsertMetadata.class);

	private static String EXPECTED_EXTENSION_MODULES = "expectedExtensionModules";
	
	protected UpsertMetadata(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		UpsertMetadata fix = new UpsertMetadata(null);
		try {
			ReportSheetManager.setTargetFolderId("12F1HkjI0eHRxxz9h8bDlefe6vayqCCLr");  //Configuration Update
			fix.headers = "CodeSystem, Severity, Action, Detail";
			fix.additionalReportColumns="";
			fix.selfDetermining = true;
			fix.init(args);
			fix.postInit();
			fix.upsertMetadata();
		} finally {
			fix.finish();
		}
	}

	private void upsertMetadata() throws TermServerScriptException {
		//Work through all code systems
		for (CodeSystem cs : tsClient.getCodeSystems()) {
			//We'll skip any CS that has not actually been published
			if (cs.getLatestVersion() == null) {
				continue;
			}
			LOGGER.info("Processing {}", cs);
			//Recover that particular branch
			Branch b = tsClient.getBranch(cs.getBranchPath());
			Metadata m = b.getMetadata();
			if (m.getDefaultModuleId() == null) {
				LOGGER.info("Skipping {} due to missing default moduleId", b);
				continue;
			}
			LOGGER.info("Default moduleId: {}", m.getDefaultModuleId());
			List<String> expectedExtensionModules = List.of(m.getDefaultModuleId());
			if (cs.getShortName().equals("SNOMEDCT-NO")) {
				expectedExtensionModules = List.of("57101000202106", "51000202101", "57091000202101");
			}
			if (cs.getShortName().equals("SNOMEDCT-CH")) {
				expectedExtensionModules = List.of("2011000195101", "11000241103");
			}
			Map<String, Object> metadata = new HashMap<>();
			metadata.put(EXPECTED_EXTENSION_MODULES, expectedExtensionModules);
			if (!dryRun) {
				tsClient.updateMetadata(b.getPath(), metadata);
			}
			report(PRIMARY_REPORT, cs.getShortName(), Severity.LOW, ReportActionType.CONFIGURATION_UPDATED, EXPECTED_EXTENSION_MODULES + " -> " + Iterables.toString(expectedExtensionModules));
		}
	}
}
