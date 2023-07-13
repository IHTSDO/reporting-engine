package org.ihtsdo.termserver.scripting.delta.oneOffs;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.RF2Constants.ReportActionType;
import org.ihtsdo.otf.RF2Constants.Severity;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
	MSSP-1750 We have a number of en-gb langrefset entries in the US Edition that should not be there
	They cause issues with triggering validation that complains about the lack of GB PT.
	Inactivate all.
*/
public class MSSP1750_RemoveUSLangRefsetENGB extends DeltaGenerator implements ScriptConstants{
	
	public static String US_MODULE = "731000124108";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MSSP1750_RemoveUSLangRefsetENGB delta = new MSSP1750_RemoveUSLangRefsetENGB();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.moduleId = US_MODULE;
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions
			delta.init(args);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			delta.process();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	private void process() throws ValidationFailure, TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (!inScope(d)) {
					continue;
				}
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET)) {
					String origET = l.getEffectiveTime();
					Severity severity = StringUtils.isEmpty(origET) ? Severity.MEDIUM : Severity.LOW;
					l.setActive(false);
					if (!l.getModuleId().contentEquals(US_MODULE)) {
						throw new IllegalArgumentException("Non US Module LangRefsetEntry encountered: " + l);
					}
					report(c, severity, ReportActionType.REFSET_MEMBER_INACTIVATED, l, origET, gl.getDescription(l.getReferencedComponentId()));
					outputRF2(d);
					incrementSummaryInformation("LangRefsetEntries inactivated");
				}
			}
		}
	}

}
