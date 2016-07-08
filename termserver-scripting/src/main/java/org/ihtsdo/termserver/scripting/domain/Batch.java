package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;

public class Batch {
	String batchName;
	ArrayList<Task> tasks = new ArrayList<Task>();
	
	public Batch(String fileName) {
		batchName = fileName;
		tasks = new ArrayList<Task>();
	}
	
	public Task addNewTask() {
		Task task = new Task(this);
		tasks.add(task);
		return task;
	}
	
	public Task insertNewTask(Task after) {
		Task task = new Task(this);
		tasks.add(tasks.indexOf(after), task);
		return task;
	}

	public ArrayList<Task> getTasks() {
		return tasks;
	}
	
	public Task getLastTask() {
		return tasks.get(tasks.size() -1 );
	}

	public String getBatchName() {
		return batchName;
	}

	public void merge(Task thisLargeTask, Task thisSmallTask) {
		thisLargeTask.addAll(thisSmallTask.getConcepts());
		tasks.remove(thisSmallTask);
	}

	//This needs to be dynamic because tasks move around, get merged, etc.
	public String getTaskName(Task task) {
		return batchName + ": " + (tasks.indexOf(task) + 1);
	}

}
