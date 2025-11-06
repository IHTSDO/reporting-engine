package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
DRUGS-617 For matching concepts, clone, remodel withouse presentation and replace
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloneRemodelAndReplace extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(CloneRemodelAndReplace.class);

	//All of SNOMED - see if these concepts are used anywhere
	Set<Concept> allStatedTargets = new HashSet<>();
	Set<Concept> allInferredTargets = new HashSet<>();
	public static final String PRESENTATION = "presentation";
	DrugTermGenerator termGenerator;
	
	Concept HPSNV;
	Concept HCSNV;
	
	Map<String, Concept> allKnownCDs = new HashMap<>();
	InactivationIndicator inactivationReason = InactivationIndicator.AMBIGUOUS;
	
	protected CloneRemodelAndReplace(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CloneRemodelAndReplace fix = new CloneRemodelAndReplace(null);
		try {
			ReportSheetManager.targetFolderId="1NjajGe-IjjjNk0d8lT838L4iQq_6wXrw"; //Drugs/ConeAndReplace
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.keepIssuesTogether = true;
			//fix.classifyTasks = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT_COUNT, ATTRIBUTE_COUNT";
			fix.init(args);
			fix.loadProjectSnapshot(false); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//Find all concepts that appear as attributes of another concept
		for (Concept c : gl.getAllConcepts()) {
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!r.getType().equals(IS_A)) {
					allInferredTargets.add(r.getTarget());
				}
			}
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!r.getType().equals(IS_A)) {
					allStatedTargets.add(r.getTarget());
				}
			}
		}
		
		termGenerator = new DrugTermGenerator(this);
		HPSNV = gl.getConcept("732944001 |Has presentation strength numerator value (attribute)|");
		HCSNV = gl.getConcept("733724008 |Has concentration strength numerator value (attribute)|");
	
		for (Concept c : gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT)) {
			SnomedUtils.populateConceptType(c);
			if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				allKnownCDs.put(c.getFsn(), c);
			}
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		
		//Can we safely save this clone before we inactivate the original?
		if (!loadedConcept.isActive()) {
			report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept is already inactive");
		} else if (isSafeToInactivate(task, concept)) {  //Need to use local version since loadedConcept will not have parents populated
			changesMade = cloneRemodelAndReplace(task, loadedConcept);
			if (changesMade > 0) {
				try {
					updateConcept(task, loadedConcept, info);
				} catch (Exception e) {
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
				}
			}
		}
		return changesMade;
	}

	private boolean isSafeToInactivate(Task t, Concept c) throws TermServerScriptException {
		//If this concept has stated or inferred children OR if it's used as the target to some relationship, then 
		//it's not safe to inactivate
		String msg = "Concept is not safe to inactivate. ";

		if (c.getChildren(CharacteristicType.STATED_RELATIONSHIP).size() > 0 ||
			c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
			msg += "It has descendants";
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			return false;
		}
		
		if (allStatedTargets.contains(c) || allInferredTargets.contains(c)) {
			msg += "It is used as the target of a relationship";
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			return false;
		}
		
		return true;
	}

	private int cloneRemodelAndReplace(Task t, Concept c) throws TermServerScriptException {
		SnomedUtils.populateConceptType(c);
		Concept clone = c.clone();
		//Remove all "Presenation" attributes
		for (Relationship r : clone.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().getFsn().contains(PRESENTATION)) {
				removeRelationship(t, clone, r);
			}
		}
		//And reterm
		termGenerator.ensureTermsConform(t, clone, null, CharacteristicType.STATED_RELATIONSHIP);
		
		//Have we already got one of these?  If not, create it, otherwise re-use
		Concept cd = allKnownCDs.get(clone.getFsn());
		if (cd == null) {
			//Save clone to TS
			clone = createConcept(t, clone, " cloned from " + c);
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_ADDED, clone.toString());
			t.addAfter(clone, c);
			cd = clone;
			allKnownCDs.put(clone.getFsn(), clone);
		} else {
			report(t, c, Severity.LOW, ReportActionType.INFO, "Reusing existing concept for historical association", cd);
		}
		
		//If the save of the clone didn't throw an exception, we can inactivate the original
		inactivateConcept(t, c, cd, inactivationReason);
		return CHANGE_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.debug("Identifying concepts to process");
		//We're looking for concepts with both concentration and presentation strength
		List<Concept> allAffected = gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT)
				.stream()
				.filter(c -> c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HPSNV, ActiveState.ACTIVE).size() > 0)
				.filter(c -> c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HCSNV, ActiveState.ACTIVE).size() > 0)
				.collect(Collectors.toList());
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		
		//We'll populate the first ingredient as the issue, and then sort on issue to ensure
		//like concepts are batched together
		for (Concept c : allAffected) {
			//Group 1 must contain a BoSS, or so help me...
			Concept boss = SnomedUtils.getTarget(c, new Concept[] {HAS_BOSS}, 1, CharacteristicType.STATED_RELATIONSHIP);
			c.addIssue(boss.getPreferredSynonym());
		}
		allAffected.sort(Comparator.comparing(c -> c.getIssues(",")));
		return new ArrayList<Component>(allAffected);
	}

}
