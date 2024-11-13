package org.ihtsdo.termserver.scripting.delta.ms;

import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * ISRS-867 Concepts have FSNs in DK that have no langrefset entry in the expected module
 */

public class CreateMissingLangRefsetEntries extends DeltaGenerator implements ScriptConstants {

	String langRefsetId = "554461000005103";
	
	public static void main(String[] args) throws TermServerScriptException {
		CreateMissingLangRefsetEntries delta = new CreateMissingLangRefsetEntries();
		delta.sourceModuleIds = Set.of("554471000005108");
		delta.additionalReportColumns = "FSN,SemTag,Severity,Action,Detail,Rel Id,";
		delta.standardExecution(args);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions()) {
				if (d.isActiveSafely() && sourceModuleIds.contains(d.getModuleId())
						&& d.getType().equals(DescriptionType.FSN) 
						&& !d.isPreferred() ) {
					//Let's give it a language refset entry in the appropriate module
					LangRefsetEntry l = LangRefsetEntry.withDefaults(d, langRefsetId, SCTID_PREFERRED_TERM);
					l.setModuleId(targetModuleId);
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
