package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ComponentType;

public abstract class Component {
	
	//Generic debug string to say if concept should be highlighted for some reason, eg cause a template match to fail
	String issues = "";

	public abstract String getId();
	
	public abstract String getReportedName();
	
	public abstract String getReportedType();
	
	public abstract ComponentType getComponentType();
	
	public abstract String[] toRF2() throws TermServerScriptException;
	
	public void addIssue(String issue) {
		if (!this.issues.isEmpty()) {
			this.issues += ", ";
		}
		this.issues += issue;
	}
	
	public String getIssues() {
		return issues;
	}

	public void setIssue(String issue) {
		issues = issue;
	}

	
}
