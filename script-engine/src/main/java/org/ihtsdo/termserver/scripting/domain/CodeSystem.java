
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class CodeSystem {

	@SerializedName("shortName")
	@Expose
	private String shortName;
	@SerializedName("latestVersion")
	@Expose
	private CodeSystemVersion latestVersion;

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

}