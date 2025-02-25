package org.ihtsdo.termserver.scripting.pipeline;

public abstract class ExternalConcept {

	protected String externalIdentifier;
	
	protected String property;

	public String getExternalIdentifier() {
		return externalIdentifier;
	}

	public void setExternalIdentifier(String externalIdentifier) {
		this.externalIdentifier = externalIdentifier;
	}

	public String getProperty() {
		return property;
	}

	public void setProperty(String property) {
		this.property = property;
	}
	
	public abstract boolean isHighUsage();

	public abstract boolean isHighestUsage();
	
	public abstract String getLongCommonName();
}
