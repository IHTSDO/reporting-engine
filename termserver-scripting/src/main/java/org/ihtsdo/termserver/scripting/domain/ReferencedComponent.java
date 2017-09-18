
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ReferencedComponent {

    @SerializedName("id")
    @Expose
    private String id;

    /**
     * No args constructor for use in serialization
     * 
     */
    public ReferencedComponent() {
    }

    /**
     * 
     * @param id
     */
    public ReferencedComponent(String id) {
        super();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
