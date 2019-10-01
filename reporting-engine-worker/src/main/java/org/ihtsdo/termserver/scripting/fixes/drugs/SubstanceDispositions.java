package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/*
For SUBST-244
Driven by a text file of dispositions and application points:

The top level substance (where the substance name == the disposition name) doesn't change parents, 
but gets given the disposition and gets marked as sufficiently defined. 

The next level down get reparented with the grandparent instead of the disposition-as-substance
 and gets the disposition instead as an attribute. So they will all classify back into the same place.

The NEXT level down will keep the existing parents and just gain the disposition attribute.
*/
public class SubstanceDispositions extends DrugBatchFix implements RF2Constants{
	
	Map<Concept, List<Concept>> conceptDispositionMap = new HashMap<>();
	BiMap<Concept, Concept> topLevelSubstances = HashBiMap.create();
	
	protected SubstanceDispositions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		SubstanceDispositions fix = new SubstanceDispositions(null);
		try {
			fix.groupByIssue = true;
			fix.inputFileHasHeaderRow = false;
			fix.runStandAlone = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Need full descriptions so we can get PT of target (not loaded from TS)
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changes = 0;
		for (Concept disposition : conceptDispositionMap.get(loadedConcept)) {
			changes += modelDisposition (t, loadedConcept, disposition);
		}
		
		removeRedundancy(t, loadedConcept, HAS_DISPOSITION, UNGROUPED);
		
		updateConcept(t, loadedConcept, info);
		return changes;
	}

	private int modelDisposition(Task t, Concept c, Concept disposition) throws TermServerScriptException {
		
		int changes = 0;
		//There are 3 possible behaviours here for toplevel, next level and subsequent child.
		//But in all cases we add the disposition if required
		changes += replaceRelationship(t, c, HAS_DISPOSITION, disposition, UNGROUPED, false);
		
		//We're going to check for level 2 first, because top level substances can also be level 2 for some other disposition.
		//Case 1 - Top Level
		if (c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream().anyMatch(topLevelSubstances.keySet()::contains)) {
			//Case 2 - Next level.  Our parent is a top level substance, so we'll take the grandparent as a parent and infer the disposition substance
			Concept topLevelSubstance = topLevelSubstances.inverse().get(disposition);
			List<Concept> grandParents = getGrandparents(topLevelSubstance);
			for (Concept grandParent : grandParents) {
				changes += replaceParent(t, c, topLevelSubstance, grandParent);
			}
		}
		
		if (topLevelSubstances.containsKey(c)) {
			//Case 1 - top level substance
			//Just add the disposition and make sufficiently defined so everything else with this disposition falls into line
			if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
				c.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				report (t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Made sufficiently defined");
				changes++;
			}
		} 

		//Case 3 - subsequent children just get given the disposition
		//So nothing extra to do
		return changes;
	}

	private List<Concept> getGrandparents(Concept topLevelSubstance) {
		boolean changesMade = false;
		List<Concept> grandParents = topLevelSubstance.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		//Check if any of those are top level substances themselves, take parents' parents if so.
		do {
			List<Concept> remove = new ArrayList<>();
			List<Concept> replacements = new ArrayList<>();
			changesMade = false;
			for (Concept parent : grandParents) {
				if (topLevelSubstances.containsKey(parent)) {
					remove.add(parent);
					replacements = parent.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
					changesMade = true;
				}
			}
			grandParents.addAll(replacements);
			grandParents.removeAll(remove);
		}while (changesMade);
		return grandParents;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept topLevelSubstance = gl.getConcept(lineItems[0]);
		Concept disposition = gl.getConcept(lineItems[1]);
		topLevelSubstances.put(topLevelSubstance, disposition);
		priorityComponents.add(topLevelSubstance);
		topLevelSubstance.setIssue("top level disposition");
		Set<Concept> subsumedConcepts = topLevelSubstance.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, true);
		for (Concept descendant : subsumedConcepts) {
			//We'll group our batches by disposition 
			if (descendant.getIssues().isEmpty()) {
				descendant.setIssue(disposition.toString());
			} 
			//we might have multiple dispositions for a concept
			List<Concept> dispositions = conceptDispositionMap.get(descendant);
			if (dispositions == null) {
				dispositions = new ArrayList<Concept>();
				conceptDispositionMap.put(descendant, dispositions);
			}
			dispositions.add(disposition);
			if (dispositions.size() > 1) {
				String dispositionStr = dispositions.stream().map(Concept::getFsn)
										.collect(Collectors.joining(", "));
				TermServerScript.warn(descendant + " has multiple dispositions: " + dispositionStr); 
			}
		}
		return new ArrayList<>(subsumedConcepts);
	}
}
