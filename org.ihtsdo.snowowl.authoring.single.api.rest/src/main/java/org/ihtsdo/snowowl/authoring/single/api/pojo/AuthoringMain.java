package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.b2international.snowowl.core.branch.Branch;
import com.fasterxml.jackson.annotation.JsonRawValue;

public class AuthoringMain {

	private final String key;
	private String latestClassificationJson;
	private final String validationStatus;
	private Branch.BranchState branchState;

	public AuthoringMain(String key, Branch.BranchState branchState, String validationStatus, String latestClassificationJson) {
		this.key = key;
		this.branchState = branchState;
		this.validationStatus = validationStatus;
		this.latestClassificationJson = latestClassificationJson;
	}

	public String getKey() {
		return key;
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
