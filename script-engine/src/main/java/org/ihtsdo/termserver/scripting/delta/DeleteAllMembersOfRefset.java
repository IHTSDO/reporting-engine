package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.TaskHelper;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

public class DeleteAllMembersOfRefset extends DeltaGenerator implements ScriptConstants{

	//This is a bit of a hybrid between our two types of fixes - API and Delta
	//We're going to delete the refset members in a task, but also output them to a file
	//so we have a backup of what's been removed.

	private static final String REFSET_OF_INTEREST = "900000000000513000";
	private Task task;

	public static void main(String[] args) throws TermServerScriptException {
		DeleteAllMembersOfRefset fix = new DeleteAllMembersOfRefset();
		try {
			fix.runStandAlone = false;
			fix.newIdsRequired = false;
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.getArchiveManager().setLoadOtherReferenceSets(true);
			fix.init(args);
			fix.additionalReportColumns = "Active, Details";
			fix.postInit(GFOLDER_ADHOC_UPDATES);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.deleteAllRefsetMembers();
		} finally {
			fix.finish();
		}
	}

	public void deleteAllRefsetMembers() throws TermServerScriptException {
		TaskHelper taskHelper = new TaskHelper(this, 5, true, "Delete all members of refset " + REFSET_OF_INTEREST);
		task = taskHelper.createTask();
		tsClient.getMembersByReferenceSet(task.getBranchPath(), REFSET_OF_INTEREST).forEach(this::deleteRefsetMember);
	}

	private void deleteRefsetMember(RefsetMember rm) {
		//Output the refset member to a file so we can recreate it if necessary
		try {
			outputRF2(Component.ComponentType.SIMPLE_MAP, rm.toRF2());
			tsClient.deleteRefsetMember(rm.getId(), task.getBranchPath(), true);
		} catch (TermServerScriptException e) {
			reportSafely(PRIMARY_REPORT, task, "Failed to delete refset member " + rm.getId() + " due to " + e.getMessage());
		}

		reportSafely(PRIMARY_REPORT, task, rm.isActive(), rm);
	}
}
