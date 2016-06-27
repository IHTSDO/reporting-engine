package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Batch {
	String batchName;
	List<Task> tasks = new ArrayList<Task>();
	
	public Batch(String fileName) {
		batchName = fileName;
		tasks = new ArrayList<Task>();
		addNewTask(); //Task 0 will always be for remaining concepts
	}
	
	public Task addNewTask() {
		Task task = new Task();
		tasks.add(task);
		task.setDescription(batchName + ": " + tasks.size());
		return task;
	}

	public List<Task> getTasks() {
		return tasks;
	}
	
	public Task getLastTask() {
		return tasks.get(tasks.size() -1 );
	}
	
	public void addTask(Task t) {
		tasks.add(t);
	}
	
	public boolean isRemainder() {
		return tasks.size() == 1;
	}
	
	public void addToRemainder(Collection<Concept> sameIngredientConcepts) {
		tasks.get(0).addAll(sameIngredientConcepts);
	}
	public String getBatchName() {
		return batchName;
	}

	public void addToRemainder(Concept thisConcept) {
		tasks.get(0).add(thisConcept);
	}
}
