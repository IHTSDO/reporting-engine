package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-7940
 * Delete any historically active language refset members that have been
 * left on inactive descriptions
 *
 */
public class DeleteLangRefsetsOnInactiveDescs extends BatchFix {
	
	protected DeleteLangRefsetsOnInactiveDescs(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		DeleteLangRefsetsOnInactiveDescs fix = new DeleteLangRefsetsOnInactiveDescs(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.runStandAlone = false;  //Need to look up the project for MS extensions
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			fix.init(args);
			fix.loadProjectSnapshot(false);  //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept tsConcept = loadConcept(c, t.getBranchPath());
			changesMade += inactivateLangRefsetEntries(t, tsConcept);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update refset entry for " + c, e);
		}
		return changesMade;
	}
	
	private int inactivateLangRefsetEntries(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
				ReportActionType action = l.isReleased()?ReportActionType.LANG_REFSET_INACTIVATED : ReportActionType.LANG_REFSET_DELETED;
				report(t, c, Severity.LOW, action, l);
				//Are we doing the first pass, or running for real?
				if (t != null) {
					l.setActive(false);
					if (l.isReleased()) {
						updateRefsetMember(t, l, "");
					} else {
						deleteRefsetMember(t, l.getId());
					}
				}
				changesMade++;
			}
			incrementSummaryInformation("LangRefsetEntries checked", d.getLangRefsetEntries().size());
		}
		return changesMade;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		setQuiet(true);
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (inactivateLangRefsetEntries(null, c) > 0) {
				componentsToProcess.add(c);
			}
		}
		setQuiet(false);
		return componentsToProcess;
	}

}
