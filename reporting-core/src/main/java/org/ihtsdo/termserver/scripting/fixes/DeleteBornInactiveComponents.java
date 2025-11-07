package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.util.List;

public class DeleteBornInactiveComponents extends BatchFix implements ScriptConstants {

	protected DeleteBornInactiveComponents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteBornInactiveComponents fix = new DeleteBornInactiveComponents(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.worksWithConcepts = false; //Ensures doFix is called with Component
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Component c, String info) throws TermServerScriptException {
		if (c instanceof Relationship r) {
			deleteRelationship(t, r);
		} else if (c instanceof RefsetMember) {
			throw new NotImplementedException();
		} else if (c instanceof Concept) {
			throw new NotImplementedException();
		} else if (c instanceof Description) {
			throw new NotImplementedException();
		} else {
			throw new TermServerScriptException("Unable to delete component of type " + c.getClass().getSimpleName());
		}
		return CHANGE_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllComponents().stream()
				.filter(c -> !c.isReleased())
				.filter(c -> !c.isActive())
				.toList();
	}
	
}
