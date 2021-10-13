package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * ISRS-867 Concepts have FSNs in DK that have no langrefset entry in the expected module
 */
public class CreateMissingLangRefsetEntries extends DeltaGenerator implements ScriptConstants {

	String langRefsetId = "554461000005103";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CreateMissingLangRefsetEntries delta = new CreateMissingLangRefsetEntries();
		try {
			delta.moduleId = "554471000005108";
			delta.runStandAlone = true;
			//delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.additionalReportColumns = "FSN,SemTag,Severity,Action,Detail,Rel Id,";
			delta.init(args);
			delta.loadProjectSnapshot(false);
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
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions()) {
				if (d.isActive() && d.getModuleId().equals(moduleId) 
						&& d.getType().equals(DescriptionType.FSN) 
						&& !d.isPreferred() ) {
					//Let's give it a language refset entry in the appropriate module
					LangRefsetEntry l = LangRefsetEntry.withDefaults(d, langRefsetId, SCTID_PREFERRED_TERM);
					l.setModuleId(moduleId);
					l.setRefsetId(langRefsetId);
					d.addLangRefsetEntry(l);
					c.setModified();
					report(c, Severity.MEDIUM, ReportActionType.LANG_REFSET_MODIFIED, l);
				}
			}
			
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(c, true);  //Will only output dirty fields.
			}
			
			getRF2Manager().flushFiles(false);
		}
	}

}
