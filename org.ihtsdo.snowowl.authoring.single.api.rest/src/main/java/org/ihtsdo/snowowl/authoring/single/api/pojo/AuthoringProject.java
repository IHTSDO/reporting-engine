package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.annotation.JsonRawValue;

public class AuthoringProject {

	private final String key;
	private final String title;
	private String latestClassificationJson;
	private final String validationStatus;

	public AuthoringProject(String key, String title, String validationStatus, String latestClassificationJson) {
		this.key = key;
		this.title = title;
		this.validationStatus = validationStatus;
		this.latestClassificationJson = latestClassificationJson;
	}

	public String getKey() {
		return key;
	}

	public String getTitle() {
		return title;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}

}
