package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.template.NormaliseTemplateCompliantConcepts;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/*
QI-816 Replace Stated Parents where safe to do so
QI-829 Similar, but also remove sufficiently defined parents where redundant
*/
public class ReplaceParents extends BatchFix implements RF2Constants{
	
	Map<Concept, Concept> parentReplacementMap = new HashMap<>();
	
	NormaliseTemplateCompliantConcepts normalizer;
	boolean restateInferredAsStated = true;
	
	Concept targetPPP;
	
	List<Concept> redundantPPPs = new ArrayList<>();
	
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
			fix.additionalReportColumns="AdditionalDetail, FurtherDetails, Details";
			fix.init(args);
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		/*subsetECL = "< 55680006 |Drug overdose (disorder)|";
		Concept overdose = gl.getConcept("1149222004 |Overdose (disorder)|");
		parentReplacementMap.put(gl.getConcept("55680006 |Drug overdose (disorder)|"), overdose);
		parentReplacementMap.put(gl.getConcept("59369008 |Accidental drug overdose (disorder)|"), overdose);
		parentReplacementMap.put(gl.getConcept("59274003 |Intentional drug overdose (disorder)|"), overdose);*/
		targetPPP = gl.getConcept("75478009 |Poisoning (disorder)|");
		subsetECL = "< " + targetPPP;
		parentReplacementMap.put(gl.getConcept("72431002 |Accidental poisoning (disorder)|"), targetPPP);
		parentReplacementMap.put(gl.getConcept("410061008 |Intentional poisoning (disorder)|"), targetPPP);
		redundantPPPs.add(gl.getConcept("417163006 |Traumatic or non-traumatic injury (disorder)|"));
		super.postInit();
		normalizer = new NormaliseTemplateCompliantConcepts(this);
		normalizer.setReportManager(getReportManager());
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceParents(t, loadedConcept);
		
		if (restateInferredAsStated) {
			int infCount = SnomedUtils.countAttributes(loadedConcept, CharacteristicType.INFERRED_RELATIONSHIP);
			int staCount = SnomedUtils.countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
			if (changesMade > 0 && infCount > staCount) {
				normalizer.restateInferredRelationships(t, loadedConcept);
			}
		}
		updateConcept(t, loadedConcept, info);
		return changesMade;
	}

	private int replaceParents(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> parentRels = new HashSet<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE));
		String psStr = parentRels.stream().map(r -> r.getTarget().toString()).collect(Collectors.joining(",\n"));
		
		//Calculate the proximal primitive parent to ensure we're not losing information
		List<Concept> ppps = determineProximalPrimitiveParents(c);
		
		//Remove the PPPs that we expect to see which we know to be redundant because there's more
		//than one way to get to the same SD
		ppps.removeAll(redundantPPPs);
		
		/*for (Concept ppp : ppps) {
			if (!ppp.equals(targetPPP) && !parentReplacementMap.containsKey(ppp)) {
				report (t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Calculated PPP indicates potential loss of information", ppp);
				return NO_CHANGES_MADE;
			}
		}*/
		if (ppps.size() > 1) {
			String pppsStr = ppps.stream().map(Concept::toString).collect(Collectors.joining(",\n"));
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple PPPs found", pppsStr, psStr);
			return NO_CHANGES_MADE;
		}
		
		boolean replacementMade = false;
		//TODO This is a special situation as we're forcing the map to a single value
		for (Relationship parentRel : parentRels) {
			if (replacementMade) {
				//Don't remove the targetPPP - it might already be there!
				if (!parentRel.getTarget().equals(ppps.get(0))) {
					c.removeRelationship(parentRel);
					changesMade ++;
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, parentRel);
				}
			} else {
				//changesMade += replaceParent(t, c, parentRel.getTarget(), targetPPP);
				changesMade += replaceParent(t, c, parentRel.getTarget(), ppps.get(0));
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
		//String subsetECL = "420057003 |Accidental poisoning caused by carbon monoxide (disorder)|";
		for (Concept c : findConcepts(subsetECL, true, true)) {
			Set<Concept> parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
			Set<Concept> sdParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP, DefinitionStatus.FULLY_DEFINED);
			if (sdParents.size() > 0 || !Collections.disjoint(parents, targetParents)) {
				//If we have a causative agent, add that as an issue so we can keep them together in tasks
				Set<Concept> causeAgents = SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
				if (causeAgents.size() > 0) {
					c.setIssue(causeAgents.iterator().next().getFsn());
				} else {
					c.setIssue(c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).iterator().next().getFsn());
				}
				toProcess.add(c);
			}
		}
		return toProcess;
	}

}
