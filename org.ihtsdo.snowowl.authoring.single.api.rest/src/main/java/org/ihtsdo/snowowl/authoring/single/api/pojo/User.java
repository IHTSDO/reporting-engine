package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class User {

	private String email;
	private String displayName;
	private String name;
	private String avatarUrl;

	public User(net.rcarz.jiraclient.User assignee) {
		email = assignee.getEmail();
		displayName = assignee.getDisplayName();
		name = assignee.getName();
		avatarUrl = assignee.getAvatarUrls().get("48x48");
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public void setAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}
}
