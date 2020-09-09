package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.annotations.*;

public class ConceptSearchRequest {
	
	@SerializedName("conceptIds")
	@Expose
	List<String> conceptIds = new ArrayList<>();
	
	@SerializedName("eclFilter")
	@Expose
	String eclFilter;
	
	public List<String> getConceptIds() {
		return conceptIds;
	}

	public void setConceptIds(List<String> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public String getEclFilter() {
		return eclFilter;
	}

	public void setEclFilter(String eclFilter) {
		this.eclFilter = eclFilter;
	}
	
	public ConceptSearchRequest withEcl(String ecl) {
		this.eclFilter = ecl;
		return this;
	}
	
	public ConceptSearchRequest withConcepts(List<Concept> concepts) {
		this.conceptIds = concepts.stream()
				.map(c -> c.getConceptId())
				.collect(Collectors.toList());
		return this;
	}
	
	public ConceptSearchRequest withConceptIds(List<String> conceptIds) {
		this.conceptIds = conceptIds;
		return this;
	}
}
