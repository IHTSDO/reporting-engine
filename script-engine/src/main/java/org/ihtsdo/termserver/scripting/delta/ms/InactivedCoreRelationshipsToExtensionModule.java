package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * INFRA-7696
 * When core relationships are inactivated in an extension, we expect that inactivation to happen
 * in the extension module.   This class detects the failure to shift module and creates a delta
 * to shift inactivated core relationships (inferred) to the specified module
 * 
 * Ah.  In fact I can't construct a valid snapshot here because this is post versioned core
 * content that won't be in the International Edition.  So I need to feed in a list of concepts 
 * affected and load them one by one.
 */
public class InactivedCoreRelationshipsToExtensionModule extends DeltaGenerator implements ScriptConstants {

	String effectiveTime = "20211015";
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivedCoreRelationshipsToExtensionModule delta = new InactivedCoreRelationshipsToExtensionModule();
		try {
			delta.moduleId = "51000202101";
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.additionalReportColumns = "FSN,SemTag,Severity,Action,Detail,Rel Id,";
			delta.init(args);
			delta.loadProjectSnapshot(true);  //Just FSN, not working with all descriptions here
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				info(delta.descIdGenerator.finish());
			}
		}
	}
	
	public void process() throws TermServerScriptException {
		for (Component component : processFile()) {
			Concept c = loadConcept(component.getId(), project.getBranchPath());
			for (Relationship rExt : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
				//Where the relationship is core and modified as part of the latest release, shift it to the target module
				if (rExt.getModuleId().equals(SCTID_CORE_MODULE) && rExt.getEffectiveTime().equals(effectiveTime)) {
					rExt.setModuleId(moduleId);
					c.setModified();
					rExt.setEffectiveTime(effectiveTime);
					String msg = "Shifted " + rExt + " to module " + rExt.getModuleId();
					report (c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, msg, rExt.getId());
				} else if (rExt.getModuleId().equals(SCTID_CORE_MODULE) && 
						( !rExt.getEffectiveTime().endsWith("0731") && !rExt.getEffectiveTime().endsWith("0131"))) {
					String msg = "Core relationship " + rExt + " has an odd effective time: " + rExt.getEffectiveTime();
					report (c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
				}
			}
			
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(c, true);  //Will only output dirty fields.
			}
		}
	}

}
