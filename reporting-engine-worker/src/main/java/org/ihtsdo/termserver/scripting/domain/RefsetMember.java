package org.ihtsdo.termserver.scripting.domain;

import java.util.Map;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RefsetMember {

	@SerializedName("active")
	@Expose
	private Boolean active;
	@SerializedName("released")
	@Expose
	private Boolean released;
	@SerializedName("releasedEffectiveTime")
	@Expose
	private Integer releasedEffectiveTime;
	@SerializedName("memberId")
	@Expose
	private String memberId;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("refsetId")
	@Expose
	private String refsetId;
	@SerializedName("referencedComponentId")
	@Expose
	private String referencedComponentId;
	@SerializedName("additionalFields")
	@Expose
	private Map<String, String> additionalFields;
	@SerializedName("referencedComponent")
	@Expose
	private ReferencedComponent referencedComponent;
	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;

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

	public String getMemberId() {
		return memberId;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
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
}
