package org.ihtsdo.snowowl.authoring.single.api.pojo;

import net.sf.json.JSONObject;

public class User {
	
	private static final String JSON_FIELD_EMAIL = "emailAddress";
	private static final String JSON_FIELD_DISPLAY_NAME = "displayName";
	private static final String JSON_FIELD_NAME = "name";
	private static final String JSON_FIELD_AVATAR = "avatarUrls";
	private static final String JSON_FIELD_AVATAR_48 = "48x48";

	private String email;
	private String displayName;
	private String name;
	private String avatarUrl;

	public User() {

	}

	public User(net.rcarz.jiraclient.User assignee) {
		email = assignee.getEmail();
		displayName = assignee.getDisplayName();
		name = assignee.getName();
		avatarUrl = assignee.getAvatarUrls().get("48x48");
	}

	public User(JSONObject userJSON) {
		email = userJSON.getString(JSON_FIELD_EMAIL);
		name = userJSON.getString(JSON_FIELD_NAME);
		displayName = userJSON.getString(JSON_FIELD_DISPLAY_NAME);
		JSONObject avatarUrls = userJSON.getJSONObject(JSON_FIELD_AVATAR);
		avatarUrl = avatarUrls.getString(JSON_FIELD_AVATAR_48);
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
