package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.collections4.ListUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.ArrayList;
import java.util.List;

public class BulkDeleteRefsetMembers extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(BulkDeleteRefsetMembers.class);

	private static final int CHUNK_SIZE = 500;

	protected BulkDeleteRefsetMembers(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		BulkDeleteRefsetMembers fix = new BulkDeleteRefsetMembers(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_UPDATES);  //Release QA
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.getArchiveManager().setPopulateReleaseFlag(true);
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.batchDeleteRefsetMembers();
		} finally {
			fix.finish();
		}
	}

	public void batchDeleteRefsetMembers() throws TermServerScriptException {
		Task task = createSingleTask();
		List<RefsetMember> refsetMembersToDelete = identifyRefsetMembersToDelete();
		LOGGER.info("Identified {} refset members to delete", refsetMembersToDelete.size());
		for (List<RefsetMember> chunk : ListUtils.partition(refsetMembersToDelete, CHUNK_SIZE)) {
			deleteRefsetMembersInBulk(task, chunk);
			report(PRIMARY_REPORT, task, "Deleted " + chunk.size() + " refset members");
		}
	}

	private void deleteRefsetMembersInBulk(Task task, List<RefsetMember> refsetMembersToDelete) throws TermServerScriptException {
		try {
			List<String> refsetIds = new ArrayList<>();
			for (RefsetMember refsetMember : refsetMembersToDelete) {
				refsetIds.add(refsetMember.getMemberId());
				report(PRIMARY_REPORT, task, "Deleting refset member ", refsetMember);
			}
			if (!isDryRun()) {
				//No need to force the deletion of unpublished refset members
				tsClient.deleteRefsetMembers(refsetIds, task.getBranchPath(), false);
			}
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Failed to delete refset members", e);
		}
	}

	private List<RefsetMember> identifyRefsetMembersToDelete() {
		//We are looking for unpublished en-gb langrefset members.  They've all got to go.
		return gl.getAllComponents().stream()
				.filter(LangRefsetEntry.class::isInstance)
				.map(c -> (RefsetMember)c)
				.filter(rm -> !rm.isReleased())
				.filter(rm -> SnomedUtils.isEmpty(rm.getEffectiveTime()))
				.filter(rm -> rm.getRefsetId().equals(GB_ENG_LANG_REFSET))
				.toList();
	}


}
