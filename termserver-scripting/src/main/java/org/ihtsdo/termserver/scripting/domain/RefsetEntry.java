
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RefsetEntry implements Component {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("released")
    @Expose
    private Boolean released;
    @SerializedName("active")
    @Expose
    private Boolean active;
    @SerializedName("moduleId")
    @Expose
    private String moduleId;
    @SerializedName("referencedComponent")
    @Expose
    private ReferencedComponent referencedComponent;
    @SerializedName("referenceSetId")
    @Expose
    private String referenceSetId;
    @SerializedName("valueId")
    @Expose
    private String valueId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public RefsetEntry() {
    }

    /**
     * 
     * @param released
     * @param id
     * @param referenceSetId
     * @param moduleId
     * @param valueId
     * @param active
     * @param referencedComponent
     */
    public RefsetEntry(String id, Boolean released, Boolean active, String moduleId, ReferencedComponent referencedComponent, String referenceSetId, String valueId) {
        super();
        this.id = id;
        this.released = released;
        this.active = active;
        this.moduleId = moduleId;
        this.referencedComponent = referencedComponent;
        this.referenceSetId = referenceSetId;
        this.valueId = valueId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getReleased() {
        return released;
    }

    public void setReleased(Boolean released) {
        this.released = released;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public ReferencedComponent getReferencedComponent() {
        return referencedComponent;
    }

    public void setReferencedComponent(ReferencedComponent referencedComponent) {
        this.referencedComponent = referencedComponent;
    }

    public String getReferenceSetId() {
        return referenceSetId;
    }

    public void setReferenceSetId(String referenceSetId) {
        this.referenceSetId = referenceSetId;
    }

    public String getValueId() {
        return valueId;
    }

    public void setValueId(String valueId) {
        this.valueId = valueId;
    }

}
