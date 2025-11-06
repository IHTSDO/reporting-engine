package org.ihtsdo.termserver.scripting.pipeline.domain;

public class ExternalConceptNull extends ExternalConcept {
	
	public ExternalConceptNull(String externalIdentifier) {
		this.externalIdentifier = externalIdentifier;
	}
	
	public ExternalConceptNull(String externalIdentifier, String property) {
		this.externalIdentifier = externalIdentifier;
		this.property = property;
	}

	@Override
	public boolean isHighUsage() {
		return false;
	}

	@Override
	public boolean isHighestUsage() {
		return false;
	}

	@Override
	public String getLongDisplayName() {
		return "Dummy Concept";
	}

	@Override
	public String getShortDisplayName() {
		return "Dummy Concept";
	}

	@Override
	public String[] getCommonColumns() {
		return new String[0];
	}

}
