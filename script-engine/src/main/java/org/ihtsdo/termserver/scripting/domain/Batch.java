package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.IBatch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Batch implements IBatch, RF2Constants {

	private static final Logger LOGGER = LoggerFactory.getLogger(Batch.class);

	private final String batchName;
	private GraphLoader gl;
	private List<Task> tasks = new ArrayList<>();
	
	public Batch(String fileName) {
		batchName = fileName;
	}
	
	public Batch(String fileName, GraphLoader gl) {
		this(fileName);
		this.gl = gl;
	}
	
	public Task addNewTask(String author) {
		return addNewTask(author, null);
	}

	public Task addNewTask(String author, String reviewer) {
		Task task = new Task(this, author, reviewer);
		tasks.add(task);
		return task;
	}

	public Task insertNewTask(Task after, String author, String reviewer) {
		Task task = new Task(this, author, reviewer);
		tasks.add(tasks.indexOf(after), task);
		return task;
	}

	public List<Task> getTasks() {
		return tasks;
	}
	
	public Task getLastTask() {
		return tasks.get(tasks.size() -1 );
	}

	public String getBatchName() {
		return batchName;
	}

	public void merge(Task thisLargeTask, Task thisSmallTask) {
		thisLargeTask.addAll(thisSmallTask.getComponents());
		if (thisSmallTask.getTaskInfo()!=null) {
			if (thisLargeTask.getTaskInfo() == null) {
				thisLargeTask.setTaskInfo(thisSmallTask.getTaskInfo());
			} else {
				//split on comma and form a unique set
				Set<String> smallInfoItems = new HashSet<>(Arrays.asList(thisSmallTask.getTaskInfo().split(", ")));
				Set<String> largeInfoItems =  new HashSet<>(Arrays.asList(thisLargeTask.getTaskInfo().split(", ")));
				largeInfoItems.addAll(smallInfoItems);
				thisLargeTask.setTaskInfo(StringUtils.join(largeInfoItems, ", "));
			}
		}
		tasks.remove(thisSmallTask);
	}

	//This needs to be dynamic because tasks move around, get merged, etc.
	public String getTaskName(Task task) {
		return batchName + ": " + (tasks.indexOf(task) + 1);
	}

	public void consolidateIntoLargeTasks(int taskSize, int wiggleRoom) {
		//Order the tasks by size and put the smallest tasks into the largest ones with space
		List<Task> smallToLarge = orderTasks(true);
		nextSmallTask:
		for (Task thisSmallTask : smallToLarge) {
			if (thisSmallTask.size() < taskSize) {
				for (Task thisLargeTask : orderTasks(false)) {
					if (thisLargeTask.size() + thisSmallTask.size() <= taskSize + wiggleRoom && !thisLargeTask.equals(thisSmallTask)) {
						LOGGER.info("Merging task {} ({}}) with {} ({})", thisLargeTask, thisLargeTask.size(), thisSmallTask, thisSmallTask.size());
						merge(thisLargeTask, thisSmallTask);
						continue nextSmallTask;
					}
				}
			}
		}
		
	}
	
	List<Task> orderTasks(boolean ascending) {
		List<Task> orderedList = new ArrayList<>(getTasks());
		Collections.sort(orderedList, new Comparator<>()
		{
			public int compare(Task t1, Task t2) 
			{
				return ((Integer)(t1.size())).compareTo(t2.size());
			}
		});
		if (!ascending) {
			Collections.reverse(orderedList);
		}
		return orderedList;
	}

	public void consolidateSimilarTasks(int taskSize, int wiggleRoom) {
		//Loop through small tasks and find other tasks that have the same taskInfo ie grouped the same
		List<Task> smallToLarge = orderTasks(true);
		for (Task smallTask : smallToLarge) {
			if (mergeSameInfoTask(smallTask, taskSize, wiggleRoom)) {
				break;
			}
			mergeSimilarInfoTask(smallTask, taskSize, wiggleRoom);
		}
	}

	private boolean mergeSameInfoTask(Task smallTask, int taskSize, int wiggleRoom) {
		for (Task candidateForMerge : tasks) {
			if (!candidateForMerge.equals(smallTask)
				&& candidateForMerge.getComponents().size() + smallTask.getComponents().size() <= taskSize + wiggleRoom
				&& smallTask.getTaskInfo().equals(candidateForMerge.getTaskInfo())) {
				merge(candidateForMerge, smallTask);
				return true;
			}
		}
		return false;
	}

	private void mergeSimilarInfoTask(Task smallTask, int taskSize, int wiggleRoom) {
		//If we haven't found a task that has exactly the same task info, lets see if we can find one of which we are a subset
		for (Task candidateForMerge : tasks) {
			if (!candidateForMerge.equals(smallTask)
					&& candidateForMerge.getComponents().size() + smallTask.getComponents().size() <= taskSize + wiggleRoom
					&& (candidateForMerge.getTaskInfo().contains(smallTask.getTaskInfo()) ||
					smallTask.getTaskInfo().contains(candidateForMerge.getTaskInfo()))) {
				merge(candidateForMerge, smallTask);
				return;
			}
		}
	}

	public void consolidateSiblingTasks(int taskSize, int wiggleRoom) throws TermServerScriptException {
		//Loop through small tasks and find other tasks that contain siblings of the concepts in this task
		List<Task> smallToLarge = orderTasks(true);
		for (Task smallTask : smallToLarge) {
			//Once the task is more than 50% full, we probabably can't consolidate any further
			if (smallTask.getComponents().size() > (0.5 * taskSize)) {
				break;
			}
			//If one of the siblings of all of the concepts in this task appear in another task
			//then merge this task into that one.
			List<List<Concept>> allConceptSiblings = getTaskConceptSiblings(smallTask, CharacteristicType.INFERRED_RELATIONSHIP);
			for (Task candidateForMerge : tasks) {
				if (mergeCandidate(smallTask, allConceptSiblings, candidateForMerge, taskSize, wiggleRoom)) {
					break;
				}
			}
		}
	}

	private boolean mergeCandidate(Task smallTask, List<List<Concept>> allConceptSiblings, Task candidateForMerge, int taskSize, int wiggleRoom) {
		if (candidateForMerge.equals(smallTask)) {
			return false;  //Try next task
		}
		//If our siblings have nothing in common with the task ie is disjoint, then reject it.
		for (List<Concept> thisConceptSiblings : allConceptSiblings) {
			if (Collections.disjoint(candidateForMerge.getComponents(), thisConceptSiblings)) {
				return false;  //Try next task
			}
		}
		if (candidateForMerge.getComponents().size() > taskSize + wiggleRoom) {
			return false;
		}
		merge (candidateForMerge, smallTask);
		return true;
	}

	private List<List<Concept>> getTaskConceptSiblings(Task task, CharacteristicType cType) throws TermServerScriptException {
		List<List<Concept>> conceptSiblings = new ArrayList<>();
		for (Component thisTaskConcept : task.getComponents()) {
			//Get the locally loaded concept which has all relationships, rather than the
			//concept held in the task, which might just be a ChangeTask 
			Concept thisLocallyLoadedConcept = gl.getConcept(thisTaskConcept.getId());
			conceptSiblings.add(thisLocallyLoadedConcept.getSiblings(cType));
		}
		return conceptSiblings;
	}

	public void setTasks(List<Task> filteredTasks) {
		this.tasks = filteredTasks;
	}
}
