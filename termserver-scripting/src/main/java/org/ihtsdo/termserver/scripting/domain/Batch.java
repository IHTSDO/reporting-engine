package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;

public class Batch {
	
	List<Task> tasks = new ArrayList<Task>();

	public List<Task> getTasks() {
		return tasks;
	}
	public void setTasks(List<Task> tasks) {
		this.tasks = tasks;
	}
	public void addTask(Task t) {
		tasks.add(t);
	}

}
