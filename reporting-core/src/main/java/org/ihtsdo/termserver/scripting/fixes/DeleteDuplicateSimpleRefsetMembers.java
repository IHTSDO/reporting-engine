package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.util.*;

public class DeleteDuplicateSimpleRefsetMembers extends BatchFix implements ScriptConstants{

	private String refsetOfInterest = "723264001";  // Lateralizeable body structure reference set

	protected DeleteDuplicateSimpleRefsetMembers(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteDuplicateSimpleRefsetMembers fix = new DeleteDuplicateSimpleRefsetMembers(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.getArchiveManager().setLoadOtherReferenceSets(true);
			fix.init(args);
			fix.additionalReportColumns = "Active, Details";
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		//We'll assume there's only one published and one inactive, and if not, exception out.
		RefsetMember publishedRM = getOtherRefsetMember(c, true);
		RefsetMember newRM = getOtherRefsetMember(c, false);

		if (!newRM.isActiveSafely()) {
			throw new TermServerScriptException("Unexpected data condition: new refset member is not active: " + newRM);
		}
		if (publishedRM.isActiveSafely()) {
			throw new TermServerScriptException("Unexpected data condition: published refset member is active: " + publishedRM);
		}

		publishedRM.setActive(true);
		updateRefsetMember(t, publishedRM, "");
		report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REACTIVATED, publishedRM);

		deleteRefsetMember(t, newRM.getId(), false);
		report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_DELETED, newRM);
		return CHANGE_MADE;
	}

	private RefsetMember getOtherRefsetMember(Concept c, boolean isReleased) throws TermServerScriptException {
		List<RefsetMember> members = c.getOtherRefsetMembers().stream()
				.filter(rm -> rm.getRefsetId().equals(refsetOfInterest))
				.filter(rm -> (isReleased == rm.isReleased()))
				.toList();
		if (members.size() > 1 || members.isEmpty()) {
			throw new TermServerScriptException("Unexpected number of refset members (" + members.size() + ") for " + c + " isReleased=" + isReleased);
		}

		return members.get(0);
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allAffected = new TreeSet<>();  //We want to process in the same order each time, in case we restart and skip some.
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			boolean alreadySeen = false;
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				if (rm.getRefsetId().equals(refsetOfInterest)) {
					if (alreadySeen) {
						allAffected.add(c);
						continue nextConcept;
					} else {
						alreadySeen = true;
					}
				}
			}
		}
		return new ArrayList<>(allAffected);
	}
	
}
