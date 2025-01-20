package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
For SUBST-200, DRUGS-448, DRUGS-451, DRUGS-466, DRUGS-484, SUBST-269, SUBST-271
Optionally driven by a text file of concepts, check parents for redundancy and - assuming 
the concept is primitive, retain the more specific parent.
*/
public class RemoveRedundantParents extends BatchFix implements ScriptConstants{
	
	String exclude = null; //"105590001"; // |Substance (substance)|
	String[] includes = {"373873005", "105590001"}; // Drugs & Substances
	
	protected RemoveRedundantParents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RemoveRedundantParents fix = new RemoveRedundantParents(null);
		try {
			fix.reportNoChange = true;
			//fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = removeRedundantParents(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int removeRedundantParents(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		
		//Make sure we're working with a Primitive Concept
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined" );
			return 0;
		}
		
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																				IS_A,
																				ActiveState.ACTIVE));
		
		for (Relationship parentRel : parentRels) {
			//If we have a more specific parent, delete or inactivate this one
			Concept moreSpecific = findMoreSpecificCoparent(parentRel.getTarget(), loadedConcept);
			if (moreSpecific != null) {
				removeParentRelationship(task, parentRel, loadedConcept, moreSpecific.toString(), null);
				changesMade++;
			}
		}
		return changesMade;
	}


	private Concept findMoreSpecificCoparent(Concept parent, Concept loadedConcept) throws TermServerScriptException {
		for (Concept coParent : getParents(loadedConcept)) {
			//Does the coParent have the parent in question as one of it's ancestors?
			if (coParent.getAncestors(NOT_SET).contains(parent)) {
				return coParent;
			}
		}
		return null;
	}

	/*
	 * Loaded concepts don't fill the parents list on the concept pojo, so we'll do that explicitly here
	 */
	private List<Concept> getParents(Concept loadedConcept) throws TermServerScriptException {
		List<Concept> parents = new ArrayList<>();
		Set<Relationship> parentRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
										IS_A,
										ActiveState.ACTIVE);
		for (Relationship r : parentRels) {
			parents.add(gl.getConcept(r.getTarget().getConceptId()));
		}
		return parents;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return Collections.singletonList(new Concept(lineItems[0]));
	}
	
	/*protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		println ("This concept causing a problem");
		List<Component> processMe = new ArrayList<>();
		processMe.add(gl.getConcept("429990006"));
		return processMe;
	}*/
	
	/*protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		info ("Identifying concepts to process");
		Collection<Concept> checkMe = new ArrayList<>();
		if (includes != null) {
			for (String include : includes) {
				checkMe.addAll(gl.getConcept(include).getDescendants(NOT_SET));
			}
		} else { 
			checkMe = gl.getAllConcepts();
		}
		
		if (exclude != null) {
			checkMe.removeAll(gl.getConcept(exclude).getDescendants(NOT_SET));
		}
		List<Concept> processMe = new ArrayList<>();
		
		nextConcept:
		for (Concept c : checkMe) {
			if (c.getDefinitionStatus() == null) {
				info ("Concept " + c.getConceptId() + " not properly imported");
			} else {
				if (c.isActive()) {
					Set<Relationship> parentRels = new HashSet<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
													IS_A,
													ActiveState.ACTIVE));
	
					for (Relationship parentRel : parentRels) {
						//If we have a more specific parent, delete or inactivate this one
						Concept moreSpecific = findMoreSpecificCoparent(parentRel.getTarget(), c);
						if (moreSpecific != null) {
							processMe.add(c);
							continue nextConcept;
						}
					}
				}
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		info ("Identified " + processMe.size() + " concepts to process");
		return asComponents(processMe);
	}*/

}
