package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;

public class ConceptCollection {
	
	List<Concept> items = new ArrayList<>();

	public List<Concept> getItems() {
		return items;
	}

	public void setItems(List<Concept> items) {
		this.items = items;
	}
}
