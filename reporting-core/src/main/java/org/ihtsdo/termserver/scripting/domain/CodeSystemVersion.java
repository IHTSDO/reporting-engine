
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class CodeSystemVersion {

	@SerializedName("shortName")
	@Expose
	private String shortName;
	@SerializedName("importDate")
	@Expose
	private String importDate;
	@SerializedName("parentBranchPath")
	@Expose
	private String parentBranchPath;
	@SerializedName("effectiveDate")
	@Expose
	private Integer effectiveDate;
	@SerializedName("version")
	@Expose
	private String version;
	@SerializedName("description")
	@Expose
	private String description;
	@SerializedName("branchPath")
	@Expose
	private String branchPath;

	public String getShortName() {
	return shortName;
	}

	public void setShortName(String shortName) {
	this.shortName = shortName;
	}

	public String getImportDate() {
	return importDate;
	}

	public void setImportDate(String importDate) {
	this.importDate = importDate;
	}

	public String getParentBranchPath() {
	return parentBranchPath;
	}

	public void setParentBranchPath(String parentBranchPath) {
	this.parentBranchPath = parentBranchPath;
	}

	public Integer getEffectiveDate() {
	return effectiveDate;
	}

	public void setEffectiveDate(Integer effectiveDate) {
	this.effectiveDate = effectiveDate;
	}

	public String getVersion() {
	return version;
	}

	public void setVersion(String version) {
	this.version = version;
	}

	public String getDescription() {
	return description;
	}

	public void setDescription(String description) {
	this.description = description;
	}

	public String getBranchPath() {
	return branchPath;
	}

	public void setBranchPath(String branchPath) {
	this.branchPath = branchPath;
	}
	
	public String toString() {
		return shortName + " on branch: " + branchPath + " - latest: " + getEffectiveDate();
	}

}