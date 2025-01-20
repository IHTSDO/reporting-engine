package org.ihtsdo.termserver.scripting.fixes.rf2Player;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.domain.Relationship;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/*
 * Splits an RF2 Archive of changes into tasks by destination of 
 * "Has Disposition" attribute.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DispositionsArchive extends Rf2Player implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(DispositionsArchive.class);

	Map<Concept, String> conceptToDispositionMap = new HashMap<Concept, String>();
	Set<Concept> hasNoDisposition = new HashSet<Concept>();	
	Set<Concept> inactivations = new HashSet<Concept>();
	
	//Temporary - select some dispositions to process
	String[] subset = new String[] { 
			"734551003",
			"734592007",
			"734596005",
			"734597001"};
	
	Concept hasDisposition;
	
	protected DispositionsArchive(DispositionsArchive clone) {
		super(clone);
		this.wiggleRoom = 2;
		this.populateEditPanel = false;
		this.allowRecentChanges = true;
	}

	public static void main(String[] args) throws TermServerScriptException, FileNotFoundException {
		new DispositionsArchive(null).playRf2Archive(args);
	}

	protected Batch formIntoBatch() throws TermServerScriptException  {
		Batch batch = new Batch(getScriptName(), gl);
		hasDisposition = gl.getConcept(726542003L); //|Has disposition (attribute)|
		Multimap<String, Concept> dispositionBuckets = getDispositionBuckets();
		int conceptsModified = 0;

		for (String bucketId : dispositionBuckets.keySet()) {
			if (dispositionBuckets.get(bucketId).size() <= taskSize ) {
				Task task = batch.addNewTask(getNextAuthor());
				task.setTaskInfo(bucketId);
				
				for (Concept concept : dispositionBuckets.get(bucketId)) {
					task.add(concept);
				}
				String bucketName = bucketId.contains("+")?bucketId : gl.getConcept(bucketId).toString();
				LOGGER.info(bucketName + ": " + task.size());
			} else {
				//Clone to prevent editing of collection while looping thorugh it.
				splitBucketIntoNeighbourhoods(batch, bucketId, new ArrayList<Concept>(dispositionBuckets.get(bucketId)));
			}
			conceptsModified += dispositionBuckets.get(bucketId).size();
		}
		
		//Now see if we can reduce the number of tasks through grouping with siblings and merging small and large tasks
		batch.consolidateSimilarTasks(taskSize, wiggleRoom);
		batch.consolidateSiblingTasks(taskSize, wiggleRoom);
		batch.consolidateIntoLargeTasks(taskSize, wiggleRoom);
		
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_TO_PROCESS, conceptsModified);
		addSummaryInformation("CONCEPTS_INACTIVATED", inactivations.size());
		addSummaryInformation("CONCEPTS_NO_DISPOSITION", hasNoDisposition.size());
		
		for (Task t : batch.getTasks()) {
			LOGGER.info (t.getKey() + " (" + t.getComponents().size() + ") " + t.getTaskInfo());
		}
		
		LOGGER.info("\n\n" + BREAK);
		LOGGER.info("Large Dispositions are: ");
		for (String bucketId : dispositionBuckets.keySet()) {
			Collection<Concept> thisBucket = dispositionBuckets.get(bucketId);
			if (thisBucket.size() > taskSize) {
				LOGGER.info (gl.getConcept(bucketId) + ": " + thisBucket.size());
			}
		}
		
		filterBatch(batch);
		
		for (Task t : batch.getTasks()) {
			LOGGER.info("Filtered: " + t.getKey() + " (" + t.getComponents().size() + ") " + t.getTaskInfo());
		}
		return batch;
	}

	private void filterBatch(Batch batch) {
		List<Task> originalTasks = new ArrayList<>(batch.getTasks());
		List<Task> filteredTasks = new ArrayList<>();
		for (Task task : originalTasks) {
			for (String subSetStr : subset) {
				if (task.getTaskInfo().contains(subSetStr)) {
					filteredTasks.add(task);
					break;
				}
			}
		}
		batch.setTasks(filteredTasks);
	}

	private void splitBucketIntoNeighbourhoods(Batch batch, String bucketId, Collection<Concept> remainingConceptsToGroup) throws TermServerScriptException {
		int initialSize = remainingConceptsToGroup.size();
		while (remainingConceptsToGroup.size() >  0) {
			Task task = batch.addNewTask(getNextAuthor());
			task.setTaskInfo(bucketId);
			//First identify the lowest concept which will initially be a leaf node
			Concept currentFocus = findLowestConcept(remainingConceptsToGroup);
			task.add(currentFocus);
			remainingConceptsToGroup.remove(currentFocus);
			
			//Now see how many other concepts we'll add if we include it's ancestor's descendants
			//This recursive function will keep calling until the task is full
			addAncestorContribution(currentFocus, task, remainingConceptsToGroup);
			LOGGER.info("Batch of " + initialSize + " split into " + task.getComponents().size());
		}
	}

	private boolean addAncestorContribution(Concept currentFocus, Task task, Collection<Concept> remainingConcepts) throws TermServerScriptException {
		//We need to work with the fully loaded concept, not the change version
		currentFocus = gl.getConcept(currentFocus.getConceptId());
		for (Concept thisAncestor : currentFocus.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Get all descendants and work out which of those we'd want to process
			Set<Concept> contribution = getAncestorContribution(thisAncestor, remainingConcepts);
			if (contribution.size() + task.getComponents().size() <= taskSize) {
				for (Component c : contribution) {
					task.add(c);
				}
				remainingConcepts.removeAll(contribution);
			} else {
				return true;
			}
		}
		
		boolean isFull =  task.getComponents().size() >= taskSize;
		while (!isFull && remainingConcepts.size() > 0) {
			for (Concept thisAncestor : currentFocus.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				isFull = addAncestorContribution(thisAncestor, task, remainingConcepts);
				if (isFull) break;
			}
		}
		return isFull;
	}

	private Set<Concept> getAncestorContribution(Concept thisAncestor, Collection<Concept> remainingConcepts) throws TermServerScriptException {
		Set<Concept> potentialContribution = thisAncestor.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);
		//But we need to add change concepts to the task, so work out which one of our remaining concepts are in that descendant list
		Set<Concept> contribution = new HashSet<Concept>();
		for (Concept changeConcept : remainingConcepts) {
			if (potentialContribution.contains(changeConcept)) {
				contribution.add(changeConcept);
			}
		}
		return contribution;
	}

	private Concept findLowestConcept( Collection<Concept> remainingConcepts) {
		Concept lowestConcept = null;
		for (Concept c : remainingConcepts) {
			if (lowestConcept == null || c.getDepth() > lowestConcept.getDepth()) {
				lowestConcept = c;
			}
		}
		return lowestConcept;
	}

	private Multimap<String, Concept> getDispositionBuckets() {
		//Disposition could be compound eg X+Y
		Multimap<String, Concept> dispositionBuckets = HashMultimap.create(); 
		for (Concept c : changingConcepts.values()) {
			//Firstly, is this an inactivation?
			if (!c.isActive()) {
				inactivations.add(c);
			} else {
				List<String> dispositions = getDispositions(c);
				if (dispositions.size() == 0) {
					hasNoDisposition.add(c);
				} else {
					String dispositionKey = StringUtils.join(dispositions, "+");
					dispositionBuckets.put(dispositionKey, c);
				}
			}
		}
		return dispositionBuckets;
	}

	private List<String> getDispositions(Concept c) {
		Set<Relationship> dispositions = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, hasDisposition, ActiveState.ACTIVE);
		List<String> dispositionIds = new ArrayList<String>();
		for (Relationship r : dispositions) {
			dispositionIds.add(r.getTarget().getConceptId());
		}
		return dispositionIds;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
