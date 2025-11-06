package org.ihtsdo.termserver.scripting.pipeline.loinc.domain;

public enum LoincClassType {
	LABORDERS("LABORDERS");

	private final String name;

	LoincClassType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
