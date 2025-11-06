package org.ihtsdo.termserver.scripting.fixes.metadata;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class CodeSystemMetadataPopulation extends BatchFix implements ScriptConstants {

	protected CodeSystemMetadataPopulation(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CodeSystemMetadataPopulation fix = new CodeSystemMetadataPopulation(null);
		try {
			fix.headers = "CodeSystem, Severity, Action, previousDependencyPackage, dependencyRelease, dependencyPackage, previousRelease, previousPackage";
			fix.additionalReportColumns="";
			fix.init(args);
			fix.postInit();
			fix.run();
		} finally {
			fix.finish();
		}
	}

	public void run() throws TermServerScriptException {
		//Lets grab all CodeSystems
		List<CodeSystem> codeSystems = tsClient.getCodeSystems();
		for (CodeSystem codeSystem : codeSystems) {
			boolean doUpdate = false;
			ReportActionType reportAction = ReportActionType.NO_CHANGE;
			Severity severity = Severity.LOW;
			//Metadata is held on the branch
			Branch branch = tsClient.getBranch(codeSystem.getBranchPath());
			String previousPackage = branch.getMetadata().getPreviousPackage();
			String previousRelease = branch.getMetadata().getPreviousRelease();
			String dependencyRelease = branch.getMetadata().getDependencyRelease();
			String dependencyPackage = branch.getMetadata().getDependencyPackage();
			String previousDependencyPackage = branch.getMetadata().getPreviousDependencyPackage();
			
			//There are some cases where we have the same dependency as the previous release!
			if (previousDependencyPackage != null &&
					previousDependencyPackage.equals("SnomedCT_InternationalRF2_PRODUCTION_20210131T120000Z.zip") &&
					previousRelease.compareTo("20210731") > 0 ) {
				reportAction = ReportActionType.CONFIGURATION_UPDATED;
				severity = Severity.HIGH;
				previousDependencyPackage = dependencyPackage;
				doUpdate = true;
			}
			
			//If we have a dependency package, then we should add a previous dependency package, if required
			if (StringUtils.isEmpty(previousDependencyPackage) && !StringUtils.isEmpty(dependencyPackage)) {
				if (previousPackage != null && previousPackage.equals("empty-rf2-snapshot.zip")) {
					previousDependencyPackage = previousPackage;
				} else {
					previousDependencyPackage = determinePreviousDependencyPackage(dependencyPackage, previousRelease);
				}
				if (previousDependencyPackage == null) {
					severity = Severity.MEDIUM;
					reportAction = ReportActionType.VALIDATION_CHECK;
					previousDependencyPackage = "<UNDETERMINED>";
				} else {
					doUpdate = true;
					reportAction = ReportActionType.CONFIGURATION_UPDATED;
				}
			}
			
			if (doUpdate) {
				Map<String, Object> metaDataUpdate = new HashMap<>();
				metaDataUpdate.put("previousDependencyPackage", previousDependencyPackage);
				if (!dryRun) {
					tsClient.updateMetadata(branch.getPath(), metaDataUpdate);
				}
			}
			report(PRIMARY_REPORT, codeSystem.getShortName(), severity, reportAction, previousDependencyPackage, dependencyRelease, dependencyPackage, previousRelease, previousPackage);
		}
	}

	private String determinePreviousDependencyPackage(String dependencyPackage, String previousRelease) {
		if (previousRelease != null && previousRelease.compareTo("20210731") > 0) {
			return "SnomedCT_InternationalRF2_PRODUCTION_20210731T120000Z.zip";
		}
		
 		switch (dependencyPackage) {
			case "xSnomedCT_InternationalRF2_PREPROD_20211031T120000Z.zip" : return "SnomedCT_InternationalRF2_PRODUCTION_20210930T120000Z.zip";
			case "SnomedCT_InternationalRF2_PRODUCTION_20210731T120000Z.zip" : return "SnomedCT_InternationalRF2_PRODUCTION_20210131T120000Z.zip";
		}
		return null;
	}

}
