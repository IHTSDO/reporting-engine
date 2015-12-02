package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.b2international.snowowl.core.branch.Branch;
import com.fasterxml.jackson.annotation.JsonRawValue;

public class AuthoringProject {

	private final String key;
	private final String title;
	private final User projectLead;
	private Branch.BranchState branchState;
	private String latestClassificationJson;
	private final String validationStatus;

	public AuthoringProject(String key, String title, User leadUser, Branch.BranchState branchState, String validationStatus, String latestClassificationJson) {
		this.key = key;
		this.title = title;
		this.projectLead = leadUser;
		this.branchState = branchState;
		this.validationStatus = validationStatus;
		this.latestClassificationJson = latestClassificationJson;
	}

	public String getKey() {
		return key;
	}

	public String getTitle() {
		return title;
	}

	public User getProjectLead() {
		return projectLead;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}
	
	public Branch.BranchState getBranchState() {
		return branchState;
	}

}
