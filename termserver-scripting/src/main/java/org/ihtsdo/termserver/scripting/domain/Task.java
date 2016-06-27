package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Task {
	String taskKey;
	String branchPath;
	String description;
	List<Concept> concepts;
	
	public Task() {
		concepts = new ArrayList<Concept>();
	}

	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getSummaryHTML() {
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
		return taskKey + ": " + getDescription();
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
}
