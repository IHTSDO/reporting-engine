package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

import java.util.UUID;

import org.ihtsdo.snowowl.authoring.batchimport.api.service.BatchImportFormat;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class BatchImportRequest {

	private UUID id;
	private String createForAuthor;
	private int conceptsPerTask;
	private BatchImportFormat format;
	private String projectKey;
	private String originalFilename;
	private MultipartFile file;
	private Integer postTaskDelay;
	private Boolean dryRun;
	private Boolean allowLateralizedContent;

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
	public BatchImportFormat getFormat() {
		return format;
	}
	@JsonIgnore
	public void setFormat(BatchImportFormat format) {
		this.format = format;
	}
	@JsonIgnore
	public String getProjectKey() {
		return projectKey;
	}
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public MultipartFile getFile() {
		return file;
	}

	public void setFile(MultipartFile file) {
		this.file = file;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public void setOriginalFilename(String originalFilename) {
		this.originalFilename = originalFilename;	
	}
	
	public String getOriginalFilename() {
		return this.originalFilename;
	}
	
	public Integer getPostTaskDelay() {
		return postTaskDelay;
	}

	public void setPostTaskDelay(Integer postTaskDelay) {
		this.postTaskDelay = postTaskDelay;
	}

	public Boolean isDryRun() {
		return dryRun;
	}

	public Boolean isLateralizedContentAllowed() {
		return allowLateralizedContent;
	}

	public void allowLateralizedContent(Boolean allowLateralizedContent) {
		this.allowLateralizedContent = allowLateralizedContent;
	}

	public void setDryRun(Boolean dryRun) {
		this.dryRun = dryRun;
	}
}
