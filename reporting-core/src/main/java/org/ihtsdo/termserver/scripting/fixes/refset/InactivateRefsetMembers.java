package org.ihtsdo.termserver.scripting.fixes.refset;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class InactivateRefsetMembers extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateRefsetMembers.class);

	private Map<Concept, List<RefsetMember>> refsetMemberDeletionMap = new HashMap<>();

	protected InactivateRefsetMembers(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateRefsetMembers fix = new InactivateRefsetMembers(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.init(args);
			fix.getArchiveManager().setRunIntegrityChecks(false);
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
		for (RefsetMember rm : refsetMemberDeletionMap.get(c)) {
			try {
				rm.setActive(false);
				updateRefsetMember(t, rm, "");
				report(c, null, Severity.LOW, "Refset member inactivated", rm);
				changesMade++;
			} catch (Exception e) {
				report(c, null, Severity.HIGH, "Failed to inactivate refset member", rm);
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		int refsetMemberCount = 0;
		for (Concept c : gl.getAllConcepts()) {
			String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
			if (semTag.equals("(navigational concept)")) {
				boolean refsetMemberFound = false;
				for (RefsetMember rm : c.getAssociationEntries(ActiveState.ACTIVE)) {
					if (rm.isActiveSafely() && rm.getRefsetId().equals(SCTID_ASSOC_MOVED_TO_REFSETID)) {
						refsetMemberDeletionMap.computeIfAbsent(c, k -> new ArrayList<>()).add(rm);
						refsetMemberCount++;
						refsetMemberFound = true;
					}
				}

				if (refsetMemberFound) {
					componentsToProcess.add(c);
				}
			}
		}
		LOGGER.info("Identified {} refset members to inactivate across {} concepts",refsetMemberCount, componentsToProcess.size());
		return componentsToProcess;
	}


}
