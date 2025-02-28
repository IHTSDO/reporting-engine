package org.ihtsdo.termserver.scripting.pipeline;

public abstract class ExternalConcept {

	protected String externalIdentifier;
	
	protected String property;

	protected ExternalConcept() {
	}

	protected ExternalConcept(String externalIdentifier) {
		this.externalIdentifier = externalIdentifier;
	}

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
	
	protected abstract String[] getCommonColumns();

	public abstract String getLongDisplayName();

	public abstract String getShortDisplayName();

	public int getPriority() {
		return 0;
	}
}
