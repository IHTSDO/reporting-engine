package org.ihtsdo.snowowl.authoring.single.api.service;

import com.fasterxml.jackson.annotation.JsonRawValue;

public class TaskAttachment {

	public String content;
	public String issueKey;
	
	public TaskAttachment() {}
	
	public TaskAttachment(String content, String issueKey) {
		this.content = content;
		this.issueKey = issueKey;
	}
	
	@JsonRawValue
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public String getIssueKey() {
		return issueKey;
	}
	public void setIssueKey(String issueKey) {
		this.issueKey = issueKey;
	}
	
	
}
