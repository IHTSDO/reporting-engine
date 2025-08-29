package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.List;

public class InactivateSimpleRefsetMembersWithInactiveReferencedComponents extends BatchFix {

	protected InactivateSimpleRefsetMembersWithInactiveReferencedComponents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateSimpleRefsetMembersWithInactiveReferencedComponents fix = new InactivateSimpleRefsetMembersWithInactiveReferencedComponents(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.getArchiveManager().setLoadOtherReferenceSets(true);
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
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
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				rm.setActive(false);
				changesMade += updateRefsetMember(t, rm, info);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, rm);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update refset entry for " + c, e);
		}
		return changesMade;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().stream()
				.filter(c -> !c.isActiveSafely())
				.filter(this::inScope)
				.filter(c -> !c.getOtherRefsetMembers().isEmpty())
				.sorted(SnomedUtils::compareSemTagFSN)
				.map(c -> (Component)c)
				.toList();
	}
}
