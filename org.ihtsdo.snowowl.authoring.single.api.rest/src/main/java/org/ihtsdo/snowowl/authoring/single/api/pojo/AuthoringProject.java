package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class AuthoringProject {

	private String key;
	private String title;

	public AuthoringProject(String key, String title) {
		this.key = key;
		this.title = title;
	}

	public String getKey() {
		return key;
	}

	public String getTitle() {
		return title;
	}
}
