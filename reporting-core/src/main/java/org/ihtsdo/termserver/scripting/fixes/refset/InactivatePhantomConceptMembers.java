package org.ihtsdo.termserver.scripting.fixes.refset;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.List;

public class InactivatePhantomConceptMembers extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivatePhantomConceptMembers.class);

	protected InactivatePhantomConceptMembers(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivatePhantomConceptMembers fix = new InactivatePhantomConceptMembers(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.init(args);
			fix.getArchiveManager().setRunIntegrityChecks(false);
			fix.getArchiveManager().setLoadOtherReferenceSets(true);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		for (RefsetMember rm : c.getOtherRefsetMembers()) {
			try {
				if (rm.isActiveSafely()) {
					rm.setActive(false);
					updateRefsetMember(t, rm, "");
					report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, rm);
					changesMade++;
				}
			} catch (Exception e) {
				report(c, null, Severity.HIGH, "Failed to inactivate refset member", rm);
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Identify all phantom concepts ie if we don't even known if it's active or not, then it doesn't
		//really exist in the concepts file
		return gl.getAllConcepts().stream()
				.filter(c -> c.getActive() == null)
				.map(c -> (Component)c)
				.toList();
	}


}
