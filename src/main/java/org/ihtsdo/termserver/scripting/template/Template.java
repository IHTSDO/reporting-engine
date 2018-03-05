package org.ihtsdo.termserver.scripting.template;

import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;

public class Template {

	char id;
	String fileName;
	LogicalTemplate logicalTemplate;

	public Template (char id, LogicalTemplate logicalTemplate, String fileName) {
		this.id = id;
		this.logicalTemplate = logicalTemplate;
		this.fileName = fileName;
	}
	
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public char getId() {
		return id;
	}

	public LogicalTemplate getLogicalTemplate() {
		return logicalTemplate;
	}
	
	public String toString () {
		return id + ": " + TemplateUtils.toString(logicalTemplate);
	}
}
