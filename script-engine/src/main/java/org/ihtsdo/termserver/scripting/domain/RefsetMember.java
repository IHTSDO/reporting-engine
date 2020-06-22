package org.ihtsdo.termserver.scripting.domain;

import java.util.Map;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

//TODO Move common fields and methods into Component itself
public abstract class RefsetMember extends Component {

	@SerializedName("active")
	@Expose
	protected  Boolean active;
	@SerializedName("released")
	@Expose
	protected  Boolean released;
	@SerializedName("releasedEffectiveTime")
	@Expose
	protected  Integer releasedEffectiveTime;
	@SerializedName("memberId")
	@Expose
	protected  String id;
	@SerializedName("moduleId")
	@Expose
	protected  String moduleId;
	@SerializedName("refsetId")
	@Expose
	protected  String refsetId;
	@SerializedName("referencedComponentId")
	@Expose
	protected  String referencedComponentId;
	@SerializedName("additionalFields")
	@Expose
	protected  Map<String, String> additionalFields;
	@SerializedName("referencedComponent")
	@Expose
	protected  ReferencedComponent referencedComponent;
	@SerializedName("effectiveTime")
	@Expose
	protected  String effectiveTime;
	
	protected String deletionEffectiveTime;
	
	protected boolean isDeleted = false;
	
	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean newActiveState) {
		if (this.active != null && !this.active == newActiveState) {
			this.effectiveTime = null;
		}
		this.active = newActiveState;
	}

	public Boolean getReleased() {
		return released;
	}

	public void setReleased(Boolean released) {
		this.released = released;
	}

	public Integer getReleasedEffectiveTime() {
		return releasedEffectiveTime;
	}

	public void setReleasedEffectiveTime(Integer releasedEffectiveTime) {
		this.releasedEffectiveTime = releasedEffectiveTime;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public void setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}

	public Map<String, String> getAdditionalFields() {
		return additionalFields;
	}

	public void setAdditionalFields(Map<String, String> additionalFields) {
		this.additionalFields = additionalFields;
	}
	
	public String getField(String key) {
		return this.additionalFields.get(key);
	}

	public ReferencedComponent getReferencedComponent() {
		return referencedComponent;
	}

	public void setReferencedComponent(ReferencedComponent referencedComponent) {
		this.referencedComponent = referencedComponent;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}
	
	public boolean isDeleted() {
		return isDeleted;
	}
}
