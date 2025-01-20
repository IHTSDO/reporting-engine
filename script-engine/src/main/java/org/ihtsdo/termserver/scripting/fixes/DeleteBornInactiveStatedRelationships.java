package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/* INFRA-2791 Delete relationships marked as inactive that were 
 * created in the current authoring cycle.
 * 
 * Find them with:
 * select id, sourceId from stated_relationship_f r1
	where r1.active = 0
	and not exists 
	(select 1 from stated_relationship_f r2
	where r1.id = r2.id
	and r2.effectiveTime < r1.effectiveTime )
	order by sourceId
*/
public class DeleteBornInactiveStatedRelationships extends BatchFix implements ScriptConstants{
	
	Map<String, Concept> relationshipMap = new HashMap<>();
	
	protected DeleteBornInactiveStatedRelationships(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteBornInactiveStatedRelationships fix = new DeleteBornInactiveStatedRelationships(null);
		try {
			ReportSheetManager.targetFolderId="15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ";  //Release QA
			fix.reportNoChange = false;
			fix.worksWithConcepts = false;
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		//Gather a map of all known inactive stated relationships
		for (Concept c : gl.getAllConcepts()) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE)) {
				relationshipMap.put(r.getId(), c);
			}
		}
		super.postInit();
	}

	@Override
	public int doFix(Task t, Component r, String info) throws TermServerScriptException, ValidationFailure {
		
		//Do we know what concept this relatinship belongs to?
		if (!relationshipMap.containsKey(r.getId())) {
			return NO_CHANGES_MADE;
		}
		Concept loadedConcept = loadConcept(relationshipMap.get(r.getId()), t.getBranchPath());
		Set<Relationship> inactiveRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE);
		for (Relationship rLoaded : inactiveRels) {
			if (rLoaded.getId().equals(r.getId()) && !rLoaded.isActive() && !rLoaded.isReleased()) {
				loadedConcept.removeRelationship(rLoaded);
				report(t, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, rLoaded);
				updateConcept(t, loadedConcept, info);
				return CHANGE_MADE;
			}
		}
		String msg = "Unable to find relationship in valid state to delete: " + r.getId();
		report(t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
		return NO_CHANGES_MADE;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		Relationship r = new Relationship();
		r.setRelationshipId(lineItems[0]);
		return Collections.singletonList(r);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		throw new TermServerScriptException("Expecting to use component version here");
	}

}
