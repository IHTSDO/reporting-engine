package org.ihtsdo.snowowl.authoring.single.api.model.work;

import org.ihtsdo.snowowl.authoring.single.api.model.Model;

import java.util.ArrayList;
import java.util.List;

public class WorkingContent implements Model {

	private String name;
	private List<WorkingConcept> concepts;
	private String taskId;

	public WorkingContent() {
		concepts = new ArrayList<>();
	}

	public WorkingContent(String name) {
		this();
		this.name = name;
	}

	public WorkingContent(String name, List<WorkingConcept> concepts) {
		this();
		this.name = name;
		this.concepts = concepts;
	}

	public WorkingContent addConcept(WorkingConcept concept) {
		concepts.add(concept);
		return this;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<WorkingConcept> getConcepts() {
		return concepts;
	}

	public void setConcepts(List<WorkingConcept> concepts) {
		this.concepts = concepts;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getTaskId() {
		return taskId;
	}
}
