package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
Fix identifies concepts that can be safely remodelled using a proximate primitives pattern
 */
public class ProximatePrimitiveRemodeller extends BatchFix implements RF2Constants{
	
	String subHierarchyStr = "64572001"; // |Disease (disorder)|
	Concept subHierarchy;
	String isaStr = "116680003";
	GraphLoader gl = GraphLoader.getGraphLoader();
	RelationshipTemplate newParent;
	int batchLimit = 1;   //Just create 1 batch for now.   0 = unlimited
	
	protected ProximatePrimitiveRemodeller(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ProximatePrimitiveRemodeller fix = new ProximatePrimitiveRemodeller(null);
		try {
			fix.useAuthenticatedCookie = true;
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postLoad();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
		} finally {
			fix.finish();
		}
	}
	
	private void postLoad() throws TermServerScriptException {
		Concept isa = gl.getConcept(isaStr);
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		newParent = new RelationshipTemplate (isa, subHierarchy, CharacteristicType.STATED_RELATIONSHIP);
	}

	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = remodel(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	/**
	 * Restates the parent as the subHierarchy root, and the inferred relationships as stated
	 * @param task
	 * @param concept
	 * @return
	 * @throws TermServerScriptException 
	 */
	private int remodel(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 1;
		setProximatePrimitiveParent(concept);
		//Get all the inferred relationships and make them stated
		for (Relationship r : concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			//The concept as loaded from the TS only populates the sourceId, not a Concept object.  Populate now.
			if (r.getSource() == null) {
				r.setSource(gl.getConcept(r.getSourceId()));
			}
			if (r.getGroupId() == UNGROUPED ) {
				//Don't need to restate IS_A relationships
				if (!r.getType().equals(IS_A)) {
					changesMade += restateUngroupedRelationship(task, r, concept);
				}
			} else {
				changesMade += restateGroupedRelationship(task, r, concept);
			}
		}
		return changesMade;
	}

	private void setProximatePrimitiveParent(Concept concept) throws TermServerScriptException {
		//Inactivate any existing IS-A stated relationships
		for (Relationship r : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(IS_A)) {
				if (r.getTarget().equals(subHierarchy)) {
					throw new TermServerScriptException ("Attempted to inactivate existing subHierarchy parent " + r);
				} else {
					r.setActive(false);
					r.setEffectiveTime(null);
				}
			}
		}
		//And add the new proximate primitive as a parent
		Relationship ppParent = newParent.createRelationship(concept, UNGROUPED, null);
		concept.addRelationship(ppParent);
	}

	private int restateUngroupedRelationship(Task task, Relationship inferred, Concept loadedConcept) {
		int changesMade = 0;
		//Does this relationship already exist stated?
		boolean alreadyExists = false;
		for (Relationship stated : inferred.getSource().getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (stated.equals(inferred)) {
				alreadyExists = true;
				String msg = "Stated Relationship already exists for " + inferred;
				report(task, inferred.getSource(), Severity.LOW, ReportActionType.DEBUG_INFO, msg);
			}
		}
		if (!alreadyExists) {
			Relationship newStated = inferred.clone(null);
			newStated.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			//Add to both the loaded concept (TS view) and the local model concept so both are in step
			loadedConcept.addRelationship(newStated);
			inferred.getSource().addRelationship(newStated);
			changesMade++;
		}
		return changesMade;
	}
	

	private int restateGroupedRelationship(Task task, Relationship inferred, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;

		//Get this inferred group
		Set<Relationship> inferredGroup = getGroup(inferred.getSource(), CharacteristicType.INFERRED_RELATIONSHIP, inferred.getGroupId());
		
		//Do we have a matching stated group?
		Set<Relationship> statedGroup = getMatchingStatedGroup(inferredGroup);
				
		//Did we find a match?
		if (statedGroup != null) {
			//If it's not brand new, log some info about that
			if (statedGroup.iterator().next().getEffectiveTime() != null) {
				String msg = "Stated Relationship Group already exists for " + inferred;
				report(task, inferred.getSource(), Severity.LOW, ReportActionType.DEBUG_INFO, msg);
			}
		} else {
			//Otherwise add this group with the lowest groupId available
			int newGroupId = SnomedUtils.getFirstFreeGroup(loadedConcept);
			for (Relationship thisGroupMember : inferredGroup) {
				Relationship newStated = thisGroupMember.clone(null);
				newStated.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				newStated.setGroupId(newGroupId);
				//Add to both the loaded concept (TS view) and the local model concept so both are in step
				loadedConcept.addRelationship(newStated);
				inferred.getSource().addRelationship(newStated);
				changesMade++;
			}
		}
		return changesMade; 
	}

	private Set<Relationship> getMatchingStatedGroup(
			Set<Relationship> inferredGroup) throws TermServerScriptException {
		Concept concept = inferredGroup.iterator().next().getSource();
		int maxGroupId = SnomedUtils.getFirstFreeGroup(concept);
		//Work through all the stated groups to see if any are a match
		
		nextGroup:
		for (long groupId=1; groupId <= maxGroupId; groupId++) {
			Set<Relationship> statedGroup = getGroup(concept, CharacteristicType.STATED_RELATIONSHIP, groupId);

			nextRel:
			for (Relationship inferred : inferredGroup) {
				boolean thisMatch = false;
				for (Relationship stated : statedGroup) {
					if (exactOrAncestorMatch(inferred, stated)) {
						thisMatch = true;
						continue nextRel;
					}
				}
				if (!thisMatch) {
					continue nextGroup;
				}
			}
			return statedGroup;
		}
		return null;
	}

	/**
	 * @return true if the inferred relationship exactly matches the stated relationship on type and target
	 * or an ancestor of the inferred relationship matches the stated target
	 * @throws TermServerScriptException 
	 */
	private boolean exactOrAncestorMatch(Relationship inferred,
			Relationship stated) throws TermServerScriptException {
		if (inferred.getType().equals(stated.getType())) {
			if (inferred.getTarget().equals(stated.getTarget())) {
				return true;  //exact match
			} else {
				Set<Concept> targetAncestors = inferred.getTarget().getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false);
				return targetAncestors.contains(stated.getTarget());
			}
		}
		return false;
	}

	//Returns all relationships in the given group
	private Set<Relationship> getGroup(Concept concept, CharacteristicType characteristicType, long goupdId) {
		Set<Relationship> groupSiblings = new HashSet<Relationship>();
		for (Relationship r : concept.getRelationships(characteristicType, ActiveState.ACTIVE)) {
			if (r.getGroupId() == goupdId) {
				groupSiblings.add(r);
			}
		}
		return groupSiblings;
	}

	protected Batch formIntoBatch() throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask(author_reviewer);
		List<Component> allConceptsToProcessed = identifyComponentsToProcess();
		int conceptsBeingProcessed = 0;
		boolean limitReached = false;
		
		for (Component thisConcept : allConceptsToProcessed) {
			conceptsBeingProcessed++;
			if (restartPosition > conceptsBeingProcessed) {
				continue;
			}
			if (!limitReached) {
				if (task.size() >= taskSize) {
					if (batchLimit >= batch.getTasks().size()) {
						limitReached = true;
						print ("Stopping collecting concepts to process at " + batchLimit + " tasks");   
					} else {
						task = batch.addNewTask(author_reviewer);
					}
				}
				if (!limitReached) {
					task.add(thisConcept);
				}
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		return batch;
	}
	

	/**
	 * Identify concepts that have a parent not equal to the subHierarchy start, and which have all fully defined ancestors
	 */
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept> outsideSubHierarchy = subHierarchy.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, true);
		Set<Concept> allDescendants = subHierarchy.getDescendents(NOT_SET);
		Set<Concept> allActiveFD = filterActiveFD(allDescendants);
		info (subHierarchy + " - " + allActiveFD.size() + "(FD) / " + allDescendants.size() + "(Active)");
		
		for (Concept thisConcept : allActiveFD) {
			try {
				List<Concept>parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP); 
				//If we have a single stated parent of disease, then we're modelled correctly
				if (parents.isEmpty()) {
					info (thisConcept + " says it has no parents!");
				} else if (!parents.get(0).getConceptId().equals(subHierarchyStr)) {
					//See if ancestors up to subHierarchy start (remove outside of that) are all fully defined
					Set<Concept> ancestors = thisConcept.getAncestors(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false);
					ancestors.removeAll(outsideSubHierarchy);  //Remove everything above our subHierarchy
					if (allFD(ancestors)) {
						processMe.add(thisConcept);
					}
				}
			} catch (TermServerScriptException e) {
				String msg = "Failed to determine status of " + thisConcept + " due to " + e;
				report(null,thisConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, msg);
			}
		}
		return processMe;
	}

	@Override
	public String getScriptName() {
		return "ProximatePrimitiveRemodeller";
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
	
	private boolean allFD(Collection<Concept> concepts) throws TermServerScriptException {
		boolean allFD = true;
		for (Concept concept : concepts) {
			if (concept.getDefinitionStatus() == null) {
				throw new TermServerScriptException(concept + " did not load properly - no definition status.  Is active?");
			}
			if (!concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				allFD = false;
				break;
			}
		}
		return allFD;
	}

	private Set<Concept> filterActiveFD(Set<Concept> fullSet) throws TermServerScriptException {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.getDefinitionStatus() == null) {
				info(thisConcept + " did not load properly - no definition status.");
			} else if (thisConcept.isActive() && thisConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

}
