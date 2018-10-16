
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Project {

	@SerializedName("key")
	@Expose
	private String key;
	@SerializedName("title")
	@Expose
	private String title;
	@SerializedName("projectLead")
	@Expose
	private ProjectLead projectLead;
	@SerializedName("branchPath")
	@Expose
	private String branchPath;
	@SerializedName("branchState")
	@Expose
	private String branchState;
	@SerializedName("validationStatus")
	@Expose
	private String validationStatus;
	@SerializedName("projectPromotionDisabled")
	@Expose
	private Boolean projectPromotionDisabled;
	@SerializedName("projectMrcmDisabled")
	@Expose
	private Boolean projectMrcmDisabled;
	@SerializedName("projectTemplatesDisabled")
	@Expose
	private Boolean projectTemplatesDisabled;
	@SerializedName("projectSpellCheckDisabled")
	@Expose
	private Boolean projectSpellCheckDisabled;
	@SerializedName("metadata")
	@Expose
	private Metadata metadata;

	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public Project() {
	}
	
	public Project(String projectKey) {
		this.key = projectKey;
	}

	/**
	 * 
	 * @param title
	 * @param branchPath
	 * @param projectSpellCheckDisabled
	 * @param projectMrcmDisabled
	 * @param projectTemplatesDisabled
	 * @param validationStatus
	 * @param branchState
	 * @param projectLead
	 * @param projectPromotionDisabled
	 * @param key
	 * @param metadata
	 */
	public Project(String key, String title, ProjectLead projectLead, String branchPath, String branchState, String validationStatus, Boolean projectPromotionDisabled, Boolean projectMrcmDisabled, Boolean projectTemplatesDisabled, Boolean projectSpellCheckDisabled, Metadata metadata) {
		super();
		this.key = key;
		this.title = title;
		this.projectLead = projectLead;
		this.branchPath = branchPath;
		this.branchState = branchState;
		this.validationStatus = validationStatus;
		this.projectPromotionDisabled = projectPromotionDisabled;
		this.projectMrcmDisabled = projectMrcmDisabled;
		this.projectTemplatesDisabled = projectTemplatesDisabled;
		this.projectSpellCheckDisabled = projectSpellCheckDisabled;
		this.metadata = metadata;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public ProjectLead getProjectLead() {
		return projectLead;
	}

	public void setProjectLead(ProjectLead projectLead) {
		this.projectLead = projectLead;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public String getBranchState() {
		return branchState;
	}

	public void setBranchState(String branchState) {
		this.branchState = branchState;
	}

	public String getValidationStatus() {
		return validationStatus;
	}

	public void setValidationStatus(String validationStatus) {
		this.validationStatus = validationStatus;
	}

	public Boolean getProjectPromotionDisabled() {
		return projectPromotionDisabled;
	}

	public void setProjectPromotionDisabled(Boolean projectPromotionDisabled) {
		this.projectPromotionDisabled = projectPromotionDisabled;
	}

	public Boolean getProjectMrcmDisabled() {
		return projectMrcmDisabled;
	}

	public void setProjectMrcmDisabled(Boolean projectMrcmDisabled) {
		this.projectMrcmDisabled = projectMrcmDisabled;
	}

	public Boolean getProjectTemplatesDisabled() {
		return projectTemplatesDisabled;
	}

	public void setProjectTemplatesDisabled(Boolean projectTemplatesDisabled) {
		this.projectTemplatesDisabled = projectTemplatesDisabled;
	}

	public Boolean getProjectSpellCheckDisabled() {
		return projectSpellCheckDisabled;
	}

	public void setProjectSpellCheckDisabled(Boolean projectSpellCheckDisabled) {
		this.projectSpellCheckDisabled = projectSpellCheckDisabled;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}
	
	public String toString() {
		return key;
	}

	public Project withBranchPath(String branchPath) {
		this.branchPath = branchPath;
		return this;
	}
	
	public boolean equals (Object other) {
		if (other instanceof Project) {
			return this.getKey().equals(((Project)other).getKey());
		}
		return false;
	}

}
