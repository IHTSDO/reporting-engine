package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;
/*
	DRUGS-510, INFRA-2415
	Script to delete unpublished inactive concepts, checking for incoming historical associations
	Driven by an input file it's not possible to spot these from only a SNAPSHOT file
*/
public class DeleteBornInactiveConcepts extends BatchFix implements ScriptConstants{
	
	protected DeleteBornInactiveConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteBornInactiveConcepts fix = new DeleteBornInactiveConcepts(null);
		try {
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		if (loadedConcept.isActive()) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active");
			return NO_CHANGES_MADE;
		}
		
		//Remove any incoming historical associations.  Actually there shouldn't be any since this concept is inactve
		if (gl.usedAsHistoricalAssociationTarget(loadedConcept).size() > 1) {
			report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is target of historical assocation");
			return NO_CHANGES_MADE;
		}
		
		deleteConcept(t, loadedConcept);
		report(t, c, Severity.LOW, ReportActionType.CONCEPT_DELETED, loadedConcept);
		return CHANGE_MADE;
	}

}
