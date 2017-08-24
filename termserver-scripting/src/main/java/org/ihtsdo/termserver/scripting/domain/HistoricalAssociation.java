package org.ihtsdo.termserver.scripting.domain;

import java.util.UUID;

//id	effectiveTime	active	moduleId	refsetId	referencedComponentId	inactivationReasonId
public class HistoricalAssociation {

	private String id;
	private String effectiveTime;
	private String moduleId;
	private Boolean active;
	private String refsetId;
	private String referencedComponentId;
	private String targetComponentId;
	private boolean dirty = false;
	private boolean isDeleted = false;
	private String deletionEffectiveTime;
	
	public HistoricalAssociation clone(String newComponentSctId) {
		HistoricalAssociation clone = new HistoricalAssociation();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = newComponentSctId;
		clone.targetComponentId = this.targetComponentId;
		clone.dirty = true; //New components need to be written to any delta
		return clone;
	}
	
	public String toString() {
		return "(" + id + ") - " + referencedComponentId + ":" + targetComponentId;
	}
	
	public String[] toRF2() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				(active?"1":"0"),
				moduleId, refsetId,
				referencedComponentId,
				targetComponentId
		};
	}
	
	public String[] toRF2Deletion() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				deletionEffectiveTime,
				(active?"1":"0"),
				"1",
				moduleId, refsetId,
				referencedComponentId,
				targetComponentId
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
		if (this.effectiveTime != null && !this.effectiveTime.isEmpty() && effectiveTime == null) {
			//Are we resetting this component to mark a change?
			dirty = true;
		}
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
			setEffectiveTime(null);
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
	public String getTargetComponentId() {
		return targetComponentId;
	}
	public void setTargetComponentId(String targetComponentId) {
		if (this.targetComponentId != null && !this.targetComponentId.equals(targetComponentId)) {
			dirty = true;
		}
		this.targetComponentId = targetComponentId;
	}

	public boolean isDirty() {
		return dirty;
	}
	
	public void setDirty() {
		dirty = true;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void delete(String deletionEffectiveTime) {
		this.isDeleted = true;
		this.deletionEffectiveTime = deletionEffectiveTime;
	}
}
