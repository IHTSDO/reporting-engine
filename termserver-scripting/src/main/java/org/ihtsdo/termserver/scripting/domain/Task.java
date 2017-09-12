package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Task {
	Batch batch;
	String taskKey;
	String branchPath;
	String summary;
	List<Concept> concepts = new ArrayList<Concept>();
	String assignedAuthor = null;
	String reviewer = null;

	/* Call Batch.addNewTask instead of creating a Task directly */
	Task(Batch batch, String[] author_reviewer) {
		this.batch = batch;
		setAuthorReviewer(author_reviewer);
	}
	
	void setAuthorReviewer(String[] author_reviewer) {
		setAssignedAuthor(author_reviewer[0]);
		if (author_reviewer.length > 1) {
			setReviewer(author_reviewer[1]);
		}
	}

	public String getSummary() {
		return batch.getTaskName(this);
	}

	public String getDescriptionHTML() {
		StringBuilder html = new StringBuilder();
		for (Concept concept : concepts) {
			html.append("<h5>").append(concept).append("</h5>\n");
		}
		return html.toString();
	}
	public List<Concept> getConcepts() {
		return concepts;
	}
	public void setConcepts(List<Concept> concepts) {
		this.concepts = concepts;
	}
	public void addConcept(Concept c) {
		concepts.add(c);
	}
	public String getBranchPath() {
		return branchPath;
	}
	public String getTaskKey() {
		return taskKey;
	}
	public void setTaskKey(String taskKey) {
		this.taskKey = taskKey;
	}
	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}
	public String toString() {
		if (taskKey != null) {
			return taskKey + ": " + getSummary();
		}
		return getSummary();
	}
	public String toQuotedList() {
		StringBuilder quotedList = new StringBuilder(concepts.size()*10).append("[");
		boolean first = true;
		for (Concept c : concepts) {
			if (!first) {
				quotedList.append(", ");
			}
			quotedList.append("\"").append(c.getConceptId()).append("\"");
			first = false;
		}
		quotedList.append("]");
		return quotedList.toString();
	}

	public void addAll(Collection<Concept> concepts) {
		this.concepts.addAll(concepts);
	}

	public void add(Concept concept) {
		this.concepts.add(concept);
	}
	
	public int size() {
		return concepts.size();
	}
	
	public String getAssignedAuthor() {
		return assignedAuthor;
	}

	public void setAssignedAuthor(String assignedAuthor) {
		this.assignedAuthor = assignedAuthor;
	}

	public String getReviewer() {
		return reviewer;
	}

	public void setReviewer(String reviewer) {
		this.reviewer = reviewer;
	}

}
