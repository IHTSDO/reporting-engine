package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;

public class Batch {
	
	String description;
	String summary;
	List<Concept> concepts;
	
	public Batch () {
		concepts = new ArrayList<Concept>();
	}
	
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
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

}
