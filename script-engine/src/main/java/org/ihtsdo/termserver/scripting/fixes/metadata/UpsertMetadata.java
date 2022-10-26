package org.ihtsdo.termserver.scripting.fixes.metadata;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.Iterables;

public class UpsertMetadata extends BatchFix implements ScriptConstants{
	
	private static String EXPECTED_EXTENSION_MODULES = "expectedExtensionModules";
	
	protected UpsertMetadata(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		UpsertMetadata fix = new UpsertMetadata(null);
		try {
			ReportSheetManager.targetFolderId = "12F1HkjI0eHRxxz9h8bDlefe6vayqCCLr";  //Configuration Update
			fix.headers = "CodeSystem, Severity, Action, Detail";
			fix.additionalReportColumns="";
			fix.selfDetermining = true;
			fix.targetAuthor = "empty";
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
			info("Processing " + cs);
			//Recover that particular branch
			Branch b = tsClient.getBranch(cs.getBranchPath());
			Metadata m = b.getMetadata();
			if (m.getDefaultModuleId() == null) {
				info("Skipping " + b + " due to missing default moduleId");
				continue;
			}
			info("Default moduleId: " + m.getDefaultModuleId());
			List<String> expectedExtensionModules = List.of(m.getDefaultModuleId());
			if (cs.getShortName().equals("SNOMEDCT-NO")) {
				expectedExtensionModules = List.of("57101000202106", "51000202101", "57091000202101");
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
