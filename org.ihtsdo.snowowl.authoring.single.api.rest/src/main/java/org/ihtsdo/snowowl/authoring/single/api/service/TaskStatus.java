package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;

public enum TaskStatus {

	NEW("New"),
	IN_PROGRESS("In Progress"),
	IN_REVIEW("In Review"),
	ESCALATION("Escalation"),
	READY_FOR_PROMOTION("Ready For Promotion"),
	PENDING("Pending");

	private final String label;

	TaskStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static TaskStatus fromLabel(String label) {
		for (TaskStatus taskStatus : TaskStatus.values()) {
			if (taskStatus.label.equals(label)) {
				return taskStatus;
			}
		}
		return null;
	}

	public static TaskStatus fromLabelOrThrow(String label) throws BusinessServiceException {
		final TaskStatus taskStatus = fromLabel(label);
		if (taskStatus == null) {
			throw new BusinessServiceException("Unrecognised task status '" + label + "'.");
		}
		return taskStatus;
	}

}
