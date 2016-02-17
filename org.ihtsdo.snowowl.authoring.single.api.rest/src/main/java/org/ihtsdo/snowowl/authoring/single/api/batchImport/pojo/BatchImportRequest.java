package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class BatchImportRequest {

	String createForAuthor;
	int conceptsPerTask;
	BatchImportFormat.FORMAT format;
	String projectKey;
	
	public BatchImportRequest(){}
	
	public String getCreateForAuthor() {
		return createForAuthor;
	}
	public void setCreateForAuthor(String createForAuthor) {
		this.createForAuthor = createForAuthor;
	}
	public int getConceptsPerTask() {
		return conceptsPerTask;
	}
	public void setConceptsPerTask(int conceptsPerTask) {
		this.conceptsPerTask = conceptsPerTask;
	}
	public BatchImportFormat.FORMAT getFormat() {
		return format;
	}
	@JsonIgnore
	public void setFormat(BatchImportFormat.FORMAT format) {
		this.format = format;
	}
	@JsonIgnore
	public String getProjectKey() {
		return projectKey;
	}
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}
}
