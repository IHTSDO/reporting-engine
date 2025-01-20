package org.ihtsdo.termserver.scripting.fixes;

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
INFRA-14250 Add additional parents where missing
*/
public class ReplaceParents extends BatchFix implements ScriptConstants{
	
	private Map<Concept, Concept> parentReplacementMap = new HashMap<>();
	private Set<Concept> addAdditionalParents = new HashSet<>();
	private Set<Concept> excludeConcepts = new HashSet<>();

	private NormaliseTemplateCompliantConcepts normalizer;
	private boolean restateInferredAsStated = true;

	private String lexicalMatch = "score";

	private Concept targetPPP;

	private List<Concept> redundantPPPs = new ArrayList<>();
	
	protected ReplaceParents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceParents fix = new ReplaceParents(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.keepIssuesTogether = false;
			fix.additionalReportColumns="AdditionalDetail, FurtherDetails, Details";
			fix.init(args);
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		targetPPP = null;
		subsetECL = "< 363788007 |Clinical history/examination observable (observable entity)| ";
		addAdditionalParents.add(gl.getConcept("782487009 |Assessment score (observable entity)|"));
		excludeConcepts = gl.getConcept("782487009 |Assessment score (observable entity)|").getDescendants(NOT_SET);
		parentReplacementMap.clear();
		redundantPPPs.clear();
		super.postInit();
		normalizer = new NormaliseTemplateCompliantConcepts(this);
		normalizer.setReportManager(getReportManager());
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = addAndReplaceParents(t, loadedConcept);
		
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

	private int addAndReplaceParents(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> parentRels = new HashSet<> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE));

		if (!parentReplacementMap.isEmpty()) {
			changesMade += replaceParentsIfRequired(t, c, parentRels);
		}

		if (!addAdditionalParents.isEmpty()) {
			changesMade += addAdditionalParents(t, c, parentRels);
		}
		return changesMade;
	}

	private int replaceParentsIfRequired(Task t, Concept c, Set<Relationship> parentRels) throws TermServerScriptException {
		int changesMade = 0;
		String psStr = parentRels.stream().map(r -> r.getTarget().toString()).collect(Collectors.joining(",\n"));
		//Calculate the proximal primitive parent to ensure we're not losing information
		List<Concept> ppps = determineProximalPrimitiveParents(c);

		//Remove the PPPs that we expect to see which we know to be redundant because there's more
		//than one way to get to the same SD
		ppps.removeAll(redundantPPPs);

		for (Concept ppp : ppps) {
			if (!ppp.equals(targetPPP) && !parentReplacementMap.containsKey(ppp)) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Calculated PPP indicates potential loss of information", ppp);
				return NO_CHANGES_MADE;
			}
		}

		if (ppps.size() > 1) {
			String pppsStr = ppps.stream().map(Concept::toString).collect(Collectors.joining(",\n"));
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple PPPs found", pppsStr, psStr);
			return NO_CHANGES_MADE;
		}

		boolean replacementMade = false;
		//This is a special situation as we're forcing the map to a single value
		for (Relationship parentRel : parentRels) {
			if (replacementMade) {
				//Don't remove the targetPPP - it might already be there!
				if (!parentRel.getTarget().equals(ppps.get(0))) {
					//Safe to force the removal of this relationship because it's in a axiom, so that will be modified rather than deleted.
					c.removeRelationship(parentRel, true);
					changesMade ++;
					report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, parentRel);
				}
			} else {
				changesMade += replaceParent(t, c, parentRel.getTarget(), ppps.get(0));
				replacementMade = true;
			}
		}
		return changesMade;
	}

	private int addAdditionalParents(Task t, Concept c, Set<Relationship> parentRels) throws TermServerScriptException {
		for (Concept additionalParent : addAdditionalParents) {
			if (parentRels.stream().anyMatch(r -> r.getTarget().equals(additionalParent))) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Additional parent already present", additionalParent);
			} else {
				Relationship r = new Relationship(c, IS_A, additionalParent, 0);
				c.addRelationship(r);
				report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, r);
				return CHANGE_MADE;
			}
		}
		return NO_CHANGES_MADE;
	}


	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		Set<Concept> targetParents = parentReplacementMap.keySet();
		for (Concept c : findConcepts(subsetECL, true, true)) {
			if (((lexicalMatch != null && c.getFsn().contains(lexicalMatch))
				|| lexicalMatch == null	)
					&& !excludeConcepts.contains(c)) {
				Set<Concept> parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
				Set<Concept> sdParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP, DefinitionStatus.FULLY_DEFINED);
				if (!sdParents.isEmpty() || (!targetParents.isEmpty() && !Collections.disjoint(parents, targetParents))
					|| (!addAdditionalParents.isEmpty() && Collections.disjoint(parents, addAdditionalParents))) {
					toProcess.add(c);
				}
			}
		}
		return toProcess;
	}

}
