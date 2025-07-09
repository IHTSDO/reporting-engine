package org.ihtsdo.termserver.scripting.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class NormaliseConceptsDriven extends NormaliseConcepts {

	public NormaliseConceptsDriven(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		NormaliseConceptsDriven app = new NormaliseConceptsDriven(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_QI_NORMALIZATION);
			app.classifyTasks = false;
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to NormaliseTemplateCompliantConcepts", e);
		} finally {
			app.finish();
		}
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		reportNoChange = false;
		summaryTabIdx = SECONDARY_REPORT;
		super.init(args);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if ((loadedConcept.getGciAxioms() != null && !loadedConcept.getGciAxioms().isEmpty())
				|| (loadedConcept.getAdditionalAxioms() != null && !loadedConcept.getAdditionalAxioms().isEmpty())) {
			throw new ValidationFailure(task, loadedConcept, "Concept uses additional or GCI axioms");
		}
		int changesMade = removeRedundandRelationships(task, loadedConcept);
		changesMade += normaliseConcept(task, loadedConcept);
		changesMade += removeRedundandGroups(task, loadedConcept);
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
		}
		return changesMade;
	}

	@Override
	protected int normaliseConcept(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;

		//Have we specified a ppp in the issues field?
		Concept newPPP = checkConceptForSpecifiedPPP(c);
		changesMade += checkAndSetProximalPrimitiveParent(t, c, newPPP, false, false);
		
		//Remove any redundant relationships, or they'll be missing from the inferred view
		changesMade += removeRedundandRelationships(t,c);
		
		//Restate inferred relationships as stated where required
		changesMade += restateInferredRelationships(t,c);
		
		//Remove stated ungrouped relationships where they're not also inferred
		changesMade += removeUngroupedRelationships(t,c);
		
		return changesMade;
	}

	protected Concept checkConceptForSpecifiedPPP(Concept loadedConcept) throws TermServerScriptException {
		Concept newPPP = null;
		//If we've just loaded this concept, we won't have the issues list populated, so switch back to the copy in memory
		Concept c = gl.getConcept(loadedConcept.getConceptId());
		if (c.getIssueList() != null && !c.getIssueList().isEmpty()) {
			String ppp = c.getIssueList().get(0);
			if (ppp != null && !ppp.isEmpty()) {
				newPPP = gl.getConcept(ppp);
				if (newPPP == null) {
					throw new TermServerScriptException("Specified PPP " + ppp + " not found in the terminology");
				}
			}
		}
		return newPPP;
	}

	@Override
	public int restateInferredRelationships(Task t, Concept c) throws TermServerScriptException {
		//Work through all inferred groups and collect any that aren't also stated, to state
		int changesMade = 0;
		List<RelationshipGroup> toBeStated = new ArrayList<>();
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		
		nextInferredGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			for (RelationshipGroup statedGroup : statedGroups) {
				if (inferredGroup.equals(statedGroup)) {
					continue nextInferredGroup;
				}
			}
			toBeStated.add(inferredGroup);
		}
		changesMade += stateRelationshipGroups(t, c, toBeStated);
		if (changesMade == 0) {
			report(t, c, Severity.NONE, ReportActionType.NO_CHANGE, "Stated/Inferred groups already matched " + statedGroups.size() + "/" + inferredGroups.size());
		}
		return changesMade;
	}

	@Override
	protected int stateRelationshipGroups(Task t, Concept c, List<RelationshipGroup> toBeStated) throws TermServerScriptException {
		int changesMade = 0;
		for (RelationshipGroup g : toBeStated) {
			//Group 0 must remain group 0.  Otherwise, find an available group number
			int freeGroup = g.getGroupId()==0?0:SnomedUtils.getFirstFreeGroup(c);
			changesMade += stateRelationshipGroup(t, c, g, freeGroup);
		}
		return changesMade;
	}

}
