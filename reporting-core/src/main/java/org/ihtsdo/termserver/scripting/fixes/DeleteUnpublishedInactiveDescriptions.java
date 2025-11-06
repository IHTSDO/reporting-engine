package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.util.*;

public class DeleteUnpublishedInactiveDescriptions extends BatchFix implements ScriptConstants{


	protected DeleteUnpublishedInactiveDescriptions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteUnpublishedInactiveDescriptions fix = new DeleteUnpublishedInactiveDescriptions(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.getArchiveManager().setRunIntegrityChecks(false);
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			fix.additionalReportColumns = "Active, Details";
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		for (Description d : c.getDescriptions()) {
			if (!d.isActiveSafely() 
					&& !d.isReleasedSafely()) {
				//Delete the refset members first
				for (LangRefsetEntry l : d.getLangRefsetEntries()) {
					deleteRefsetMember(t, l.getId(), false);
				}
				deleteDescription(t, d);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_DELETED, d);
			}
		}
		return CHANGE_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Component> allAffected = new TreeSet<>();  //We want to process in the same order each time, in case we restart and skip some.
		
		for (Concept c : gl.getAllConcepts()) {
			if (inScope(c)) {
				for (Description d : c.getDescriptions()) {
					if (!d.isActiveSafely() 
							&& !d.isReleasedSafely()) {
						allAffected.add(c);
						break;
					}
				}
			}
		}
		return new ArrayList<>(allAffected);
	}
	
}
