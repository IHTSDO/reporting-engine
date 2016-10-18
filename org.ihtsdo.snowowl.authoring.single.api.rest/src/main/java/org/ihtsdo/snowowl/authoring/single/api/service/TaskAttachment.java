package org.ihtsdo.snowowl.authoring.single.api.service;


public class TaskAttachment {

	// the attachment content
	public String content;
	
	// ticket key for which attachment was collected
	public String ticketKey;
	
	// linked ticket key (if exists)
	public String issueKey;

	public TaskAttachment() {
	}

	public TaskAttachment(String ticketKey, String issueKey, String content) {
		this.ticketKey = ticketKey;
		this.issueKey = issueKey;
		this.content = content;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	public String getTicketKey() {
		return ticketKey;
	}

	public void setTicketKey(String ticketKey) {
		this.ticketKey = ticketKey;
	}

	public String getIssueKey() {
		return issueKey;
	}

	public void setIssueKey(String issueKey) {
		this.issueKey = issueKey;
	}

}
