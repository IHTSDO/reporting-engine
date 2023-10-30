package org.ihtsdo.authoring.scheduler.api.rest.tools;

import org.snomed.otf.scheduler.domain.JobStatus;

import java.util.UUID;

public class AllReportRunnerResult {
	private String name;
	private String status;
	private String message;
	private UUID id;

	public AllReportRunnerResult(String name, JobStatus jobStatus, UUID id) {
		this.name = name;
		this.status = jobStatus.name();
		this.id = id;
	}

	public AllReportRunnerResult(String name, JobStatus jobStatus, String message) {
		this.name = name;
		this.status = jobStatus.name();
		this.message = message;
	}

	public AllReportRunnerResult(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}
}
