package org.ihtsdo.termserver.scripting.domain;

import java.util.UUID;


//id	effectiveTime	active	moduleId	refsetId	referencedComponentId	acceptabilityId
public class LangRefsetEntry {

	private String id;
	private String effectiveTime;
	private String moduleId;
	private Boolean active;
	private String refsetId;
	private String referencedComponentId;
	private String acceptabilityId;
	private boolean dirty = false;
	
	public LangRefsetEntry clone(String newDescriptionSctId) {
		LangRefsetEntry clone = new LangRefsetEntry();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = newDescriptionSctId;
		clone.acceptabilityId = this.acceptabilityId;
		clone.dirty = true; //New components need to be written to any delta
		return clone;
	}
	
	public String[] toRF2() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				(active?"1":"0"),
				moduleId, refsetId,
				referencedComponentId,
				acceptabilityId
		};
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getEffectiveTime() {
		return effectiveTime;
	}
	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}
	public String getModuleId() {
		return moduleId;
	}
	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean newActiveState) {
		if (this.active != null && this.active != newActiveState) {
			setDirty();
		}
		this.active = newActiveState;
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
	public String getAcceptabilityId() {
		return acceptabilityId;
	}
	public void setAcceptabilityId(String acceptabilityId) {
		this.acceptabilityId = acceptabilityId;
	}

	public boolean isDirty() {
		return dirty;
	}
	
	public void setDirty() {
		dirty = true;
	}
}
