package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.b2international.snowowl.core.Metadata;
import com.b2international.snowowl.core.branch.Branch;
import com.fasterxml.jackson.annotation.JsonRawValue;

public class AuthoringProject {

	private final String key;
	private final String title;
	private final User projectLead;
	private final String branchPath;
	private Branch.BranchState branchState;
	private String latestClassificationJson;
	private String validationStatus;
	private Metadata metadata;

	public AuthoringProject(String key, String title, User leadUser, String branchPath, Branch.BranchState branchState, String latestClassificationJson) {
		this.key = key;
		this.title = title;
		this.projectLead = leadUser;
		this.branchPath = branchPath;
		this.branchState = branchState;
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

	public String getBranchPath() {
		return branchPath;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	public void setValidationStatus(String validationStatus) {
		this.validationStatus = validationStatus;
	}

	@JsonRawValue
	public String getLatestClassificationJson() {
		return latestClassificationJson;
	}
	
	public Branch.BranchState getBranchState() {
		return branchState;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public Metadata getMetadata() {
		return metadata;
	}
}
