package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;

import us.monoid.json.JSONObject;

/*
For SUBST-200, DRUGS-448, DRUGS-451
Optionally driven by a text file of concepts, check parents for redundancy and - assuming 
the concept is primitive, retain the more specific parent.
*/
public class RemoveRedundantParents extends BatchFix implements RF2Constants{
	
	String exclude = null; //"105590001"; // |Substance (substance)|
	String include = "105590001"; // |Substance (substance)|
	
	protected RemoveRedundantParents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		RemoveRedundantParents fix = new RemoveRedundantParents(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			//fix.runStandAlone = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.startTimer();
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeRedundantParents(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int removeRedundantParents(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		
		//Make sure we're working with a Primitive Concept
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined" );
			return 0;
		}
		
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																				IS_A,
																				ActiveState.ACTIVE));
		
		for (Relationship parentRel : parentRels) {
			//If we have a more specific parent, delete or inactivate this one
			Concept moreSpecific = findMoreSpecificCoparent(parentRel.getTarget(), loadedConcept);
			if (moreSpecific != null) {
				remove(task, parentRel, loadedConcept, moreSpecific);
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
		List<Relationship> parentRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
										IS_A,
										ActiveState.ACTIVE);
		for (Relationship r : parentRels) {
			parents.add(gl.getConcept(r.getTarget().getConceptId()));
		}
		return parents;
	}

	private void remove(Task t, Relationship rel, Concept loadedConcept, Concept retained) {
		//Are we inactivating or deleting this relationship?
		if (rel.isReleased()) {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			report (t, loadedConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_INACTIVATED, "Inactivated parent: " + rel.getTarget() + " in favour of " + retained);
		} else {
			loadedConcept.removeRelationship(rel);
			report (t, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, "Deleted parent: " + rel.getTarget() + " in favour of " + retained);
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return new Concept(lineItems[0]);
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		println ("Identifying concepts to process");
		Collection<Concept> checkMe;
		if (include != null) {
			checkMe = gl.getConcept(include).getDescendents(NOT_SET);
		} else { 
			checkMe = gl.getAllConcepts();
		}
		
		if (exclude != null) {
			checkMe.removeAll(gl.getConcept(exclude).getDescendents(NOT_SET));
		}
		List<Component> processMe = new ArrayList<>();
		
		nextConcept:
		for (Concept c : checkMe) {
			if (c.getDefinitionStatus() == null) {
				println ("Concept " + c.getConceptId() + " not properly imported");
			} else {
				if (c.isActive()) {
					List<Relationship> parentRels = new ArrayList<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
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
		println ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

}
