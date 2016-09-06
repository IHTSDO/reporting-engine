package org.ihtsdo.snowowl.authoring.single.api.review.service;

import java.util.Date;

public class TaskMessagesDetail {

	private TaskMessagesStatus taskMessagesStatus = null;
	
	private Date viewDate = null;
	
	private Date lastMessageDate = null;

	public TaskMessagesStatus getTaskMessagesStatus() {
		return taskMessagesStatus;
	}

	public void setTaskMessagesStatus(TaskMessagesStatus taskMessagesStatus) {
		this.taskMessagesStatus = taskMessagesStatus;
	}

	public Date getViewDate() {
		return viewDate;
	}

	public void setViewDate(Date viewDate) {
		this.viewDate = viewDate;
	}

	public Date getLastMessageDate() {
		return lastMessageDate;
	}

	public void setLastMessageDate(Date lastMessageDate) {
		this.lastMessageDate = lastMessageDate;
	}

	@Override
	public String toString() {
		return "TaskMessagesDetail [taskMessagesStatus=" + taskMessagesStatus + ", viewDate=" + viewDate
				+ ", lastMessageDate=" + lastMessageDate + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lastMessageDate == null) ? 0 : lastMessageDate.hashCode());
		result = prime * result + ((taskMessagesStatus == null) ? 0 : taskMessagesStatus.hashCode());
		result = prime * result + ((viewDate == null) ? 0 : viewDate.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TaskMessagesDetail other = (TaskMessagesDetail) obj;
		if (lastMessageDate == null) {
			if (other.lastMessageDate != null)
				return false;
		} else if (!lastMessageDate.equals(other.lastMessageDate))
			return false;
		if (taskMessagesStatus == null) {
			if (other.taskMessagesStatus != null)
				return false;
		} else if (!taskMessagesStatus.equals(other.taskMessagesStatus))
			return false;
		if (viewDate == null) {
			if (other.viewDate != null)
				return false;
		} else if (!viewDate.equals(other.viewDate))
			return false;
		return true;
	}
	
	
}
