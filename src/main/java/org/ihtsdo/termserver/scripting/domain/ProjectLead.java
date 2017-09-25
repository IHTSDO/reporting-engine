
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ProjectLead {

    @SerializedName("displayName")
    @Expose
    private String displayName;
    @SerializedName("username")
    @Expose
    private String username;
    @SerializedName("avatarUrl")
    @Expose
    private String avatarUrl;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ProjectLead() {
    }

    /**
     * 
     * @param username
     * @param avatarUrl
     * @param displayName
     */
    public ProjectLead(String displayName, String username, String avatarUrl) {
        super();
        this.displayName = displayName;
        this.username = username;
        this.avatarUrl = avatarUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

}
