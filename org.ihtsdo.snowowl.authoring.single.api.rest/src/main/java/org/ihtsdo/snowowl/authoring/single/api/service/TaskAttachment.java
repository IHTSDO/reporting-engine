package org.ihtsdo.snowowl.authoring.single.api.service;

import com.fasterxml.jackson.annotation.JsonRawValue;

public class TaskAttachment {

	public String content;
	public String issueKey;

	public TaskAttachment() {
	}

	public TaskAttachment(String issueKey, String content) {
		this.issueKey = issueKey;
		this.content = content;
	}

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
