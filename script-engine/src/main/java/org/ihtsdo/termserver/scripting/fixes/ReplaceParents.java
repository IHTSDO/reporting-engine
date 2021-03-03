package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/*
QI-816 Replace Stated Parents where safe to do so
*/
public class ReplaceParents extends BatchFix implements RF2Constants{
	
	Map<Concept, Concept> parentReplacementMap = new HashMap<>();
	
	protected ReplaceParents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReplaceParents fix = new ReplaceParents(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.keepIssuesTogether = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subsetECL = "< 55680006 |Drug overdose (disorder)|";
		Concept overdose = gl.getConcept("1149222004 |Overdose (disorder)|");
		parentReplacementMap.put(gl.getConcept("55680006 |Drug overdose (disorder)|"), overdose);
		parentReplacementMap.put(gl.getConcept("59369008 |Accidental drug overdose (disorder)|"), overdose);
		parentReplacementMap.put(gl.getConcept("59274003 |Intentional drug overdose (disorder)|"), overdose);
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceParents(t, loadedConcept);
		
		//All concepts should be fully defined, if possible
		/*if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP) > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changesMade++;
				report (t, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report (t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - no attributes!");
			}
		}*/
		updateConcept(t, loadedConcept, info);
		return changesMade;
	}

	private int replaceParents(Task t, Concept c) throws TermServerScriptException {
		
		int changesMade = 0;
		Concept replacementParent = gl.getConcept("1149222004 |Overdose (disorder)|");
		Set<Relationship> parentRels = new HashSet<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		
		
		//Calculate the proximal primitive parent to ensure we're not losing information
		List<Concept> ppps = determineProximalPrimitiveParents(c);
		for (Concept ppp : ppps) {
			if (!ppp.equals(replacementParent) && !parentReplacementMap.containsKey(ppp)) {
				report (t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Calculated PPP indicates potential loss of information", ppp);
				return NO_CHANGES_MADE;
			}
		}
		
		boolean replacementMade = false;
		
		//TODO This is a special situation as we're forcing the map to a single value
		for (Relationship parentRel : parentRels) {
			if (replacementMade) {
				c.removeRelationship(parentRel);
				report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, parentRel);
			} else {
				changesMade += replaceParent(t, c, parentRel.getTarget(), replacementParent);
				replacementMade = true;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		Concept[] types = new Concept[] { CAUSE_AGENT };
		Set<Concept> targetParents = parentReplacementMap.keySet();
		for (Concept c : findConcepts(subsetECL, true, true)) {
			Set<Concept> parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
			if (!Collections.disjoint(parents, targetParents)) {
				//If we have a causative agent, add that as an issue
				SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
				toProcess.add(c);
			}
		}
		return toProcess;
	}

}
