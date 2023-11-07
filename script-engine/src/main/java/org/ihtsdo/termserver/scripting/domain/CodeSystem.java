
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CodeSystem {

	@SerializedName("name")
	@Expose
	private String name;

	@SerializedName("shortName")
	@Expose
	private String shortName;
	
	@SerializedName("latestVersion")
	@Expose
	private CodeSystemVersion latestVersion;
	
	@SerializedName("branchPath")
	@Expose
	private String branchPath;

	@SerializedName("modules")
	@Expose
	private List<Module> modules;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public CodeSystemVersion getLatestVersion() {
		return latestVersion;
	}

	public void setLatestVersion(CodeSystemVersion latestVersion) {
		this.latestVersion = latestVersion;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}
	
	@Override
	public String toString() {
		return shortName + " on branch: " + branchPath + (latestVersion == null ? "" : " - latest: " + latestVersion.getEffectiveDate());
	}


	public List<Module> getModules() {
		return modules;
	}

	public void setModules(List<Module> modules) {
		this.modules = modules;
	}

}