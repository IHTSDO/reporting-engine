package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;

public class SplitRoleGroupsWithRepeatedAttributes extends BatchFix implements ScriptConstants{
	
	Set<Concept> subHierarchy;
	List<Concept> attributesToIgnore;
	
	public SplitRoleGroupsWithRepeatedAttributes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SplitRoleGroupsWithRepeatedAttributes fix = new SplitRoleGroupsWithRepeatedAttributes(null);
		try {
			fix.inputFileHasHeaderRow = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "GroupId, RepeatedAttribute";
			fix.runStandAlone = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("46866001").getDescendants(NOT_SET); // |Fracture of lower limb (disorder)|
		attributesToIgnore = new ArrayList<Concept>();
		attributesToIgnore.add(IS_A);
	}
	
	public void setSubHierarchy(Concept concept) throws TermServerScriptException {
		this.subHierarchy = concept.getDescendants(NOT_SET);
	}

	public void setSubHierarchy(Set<Concept> concepts) {
		this.subHierarchy = concepts;
	}

	public List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		for (Concept c : this.subHierarchy) {
			if (hasRepeatedAttributeType(c, CharacteristicType.STATED_RELATIONSHIP).size() > 0 ||
					hasRepeatedAttributeType(c, CharacteristicType.INFERRED_RELATIONSHIP).size() > 0	) {
				componentsToProcess.add(c);
			}
		}
		//TODO Check for intermediate primitives
		return componentsToProcess;
	}
	
	protected Set<RelationshipGroup> hasRepeatedAttributeType (Concept c, CharacteristicType charType) {
		Set<RelationshipGroup> repeatedAttributeDetected = new HashSet<>();
		Set<Concept> attributeDetected = new HashSet<>();
		for (RelationshipGroup g : c.getRelationshipGroups(charType)) {
			attributeDetected.clear();
			for (Relationship r : g.getRelationships()) {
				//Is this an attribute of interest?
				//if (attributesToSplit.contains(r.getType())) {
				if (!attributesToIgnore.contains(r.getType())) {
					//Have we already seen it in this group?  Report if so, otherwise record sighting.
					if (attributeDetected.contains(r.getType())) {
						repeatedAttributeDetected.add(g);
						g.addIssue(r.getType());
					} else {
						attributeDetected.add(r.getType());
					}
				}
			}
		}
		return repeatedAttributeDetected;
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if (!loadedConcept.isActive()) {
			report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is recently inactive - skipping");
			return 0;
		}
		fixRepeatedAttributesInGroup(task, loadedConcept);
		
		/*for (Concept thisModifiedConcept : modifiedConcepts) {
			try {
				String conceptSerialised = gson.toJson(thisModifiedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + thisModifiedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		incrementSummaryInformation("Concepts Modified", modifiedConcepts.size());
		incrementSummaryInformation(task.getKey(), modifiedConcepts.size());
		return modifiedConcepts.size();*/
		return 1;
	}

	private void fixRepeatedAttributesInGroup(Task t, Concept loadedConcept) throws TermServerScriptException {
		int issuesReported = 0;
		for (RelationshipGroup g : hasRepeatedAttributeType(loadedConcept, CharacteristicType.STATED_RELATIONSHIP)) {
			//What types have we noted as an issue here?
			for (Concept repeatedAttributeType : g.getIssue()) {
				report(t, loadedConcept, Severity.LOW, ReportActionType.INFO, Long.toString(g.getGroupId()), repeatedAttributeType.toString());
				issuesReported++;
			}
		}
		//If we didn't find any problems in the Stated view, report the inferred view
		if (issuesReported == 0) {
			for (RelationshipGroup g : hasRepeatedAttributeType(loadedConcept, CharacteristicType.INFERRED_RELATIONSHIP)) {
				//What types have we noted as an issue here?
				for (Concept repeatedAttributeType : g.getIssue()) {
					report(t, loadedConcept, Severity.LOW, ReportActionType.INFO, Long.toString(g.getGroupId()), repeatedAttributeType.toString());
					issuesReported++;
				}
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

}
