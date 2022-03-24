package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class 

RefsetMember extends Component implements ScriptConstants {

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
	protected  Map<String, String> additionalFields = new HashMap<>();
	@SerializedName("referencedComponent")
	@Expose
	protected Object referencedComponent;
	@SerializedName("effectiveTime")
	@Expose
	protected  String effectiveTime;
	
	protected String deletionEffectiveTime;
	
	protected boolean isDeleted = false;
	
	public RefsetMember() {}
	
	public RefsetMember(String refsetId, Concept referencedCompoment, String[] additionalValues) {
		this.refsetId = refsetId;
		this.referencedComponent = referencedCompoment;
	}

	public void setEffectiveTime(String effectiveTime) {
		if (this.effectiveTime != null && !this.effectiveTime.isEmpty() && effectiveTime == null) {
			//Are we resetting this component to mark a change?
			setDirty();
		}
		this.effectiveTime = effectiveTime;
	}
	
	public String getModuleId() {
		return moduleId;
	}
	
	public void setModuleId(String moduleId) {
		if (this.moduleId != null && !this.moduleId.equals(moduleId)) {
			setDirty();
			this.effectiveTime = null;
		}
		this.moduleId = moduleId;
	}
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean newActiveState) {
		setActive(newActiveState, false);
	}
	public void setActive(boolean newActiveState, boolean forceDirty) {
		if (forceDirty || (this.active != null && this.active != newActiveState)) {
			setDirty();
			setEffectiveTime(null);
		}
		this.active = newActiveState;
	}
	public boolean isDeleted() {
		return isDeleted;
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
	
	public void setField(String key, String value) {
		this.additionalFields.put(key, value);
	}

	public void setReferencedComponent(Object referencedComponent) {
		this.referencedComponent = referencedComponent;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	@Override
	public String getReportedName() {
		throw new NotImplementedException();
	}

	@Override
	public String getReportedType() {
		return getComponentType().toString();
	}

	@Override
	public ComponentType getComponentType() {
		if (additionalFields.containsKey("targetComponentId")) {
			return ComponentType.HISTORICAL_ASSOCIATION;
		} else if (additionalFields.containsKey("acceptabilityId")) {
			return ComponentType.LANGREFSET;
		} else if (additionalFields.containsKey("valueId")) {
			return ComponentType.ATTRIBUTE_VALUE;
		}
		
		return null;
	}

	@Override
	public List<String> fieldComparison(Component other) throws TermServerScriptException {
		//TODO Add generic field comparison based off field names
		throw new NotImplementedException();
	}
	
	public Boolean isReleased() {
		return released;
	}
	
	public void delete (String deletionEffectiveTime) {
		this.deletionEffectiveTime = deletionEffectiveTime;
		this.isDeleted = true;
	}
	
	public String[] toRF2(String[] additionalFieldNames) {
		String[] rf2 =  new String[REF_IDX_FIRST_ADDITIONAL + additionalFieldNames.length];
		rf2[REF_IDX_ID] = id;
		rf2[REF_IDX_EFFECTIVETIME] = (effectiveTime==null?"":effectiveTime);
		rf2[REF_IDX_ACTIVE] = (active?"1":"0");
		rf2[REF_IDX_MODULEID] = moduleId;
		rf2[REF_IDX_REFSETID] = refsetId;
		rf2[REF_IDX_REFCOMPID] = referencedComponentId;
		for (int i=0; i <= additionalFieldNames.length; i++) {
			int idx = i + REF_IDX_FIRST_ADDITIONAL;
			rf2[idx] = getField(additionalFieldNames[i]);
		}
		return rf2;
	}
	
	public static void populatefromRf2(RefsetMember m, String[] lineItems, String[] additionalFieldNames) throws TermServerScriptException {
		m.setId(lineItems[REF_IDX_ID]);
		m.setEffectiveTime(lineItems[REF_IDX_EFFECTIVETIME]);
		m.setActive(lineItems[REF_IDX_ACTIVE].equals("1"));
		m.setModuleId(lineItems[REF_IDX_MODULEID]);
		m.setRefsetId(lineItems[REF_IDX_REFSETID]);
		m.setReferencedComponentId(lineItems[REF_IDX_REFCOMPID]);
		for (int i=0; i < additionalFieldNames.length; i++) {
			int idx = i + REF_IDX_FIRST_ADDITIONAL;
			if (lineItems.length < idx) {
				String objectName = m.getClass().getSimpleName();
				throw new TermServerScriptException(objectName + " " + m.getId() + " expected " + (idx+1) + " columns in RF2, but only contained " + lineItems.length);
			}
			m.setField(additionalFieldNames[i], lineItems[idx]);
		}
	}
	
	public String[] toRF2Deletion() {
		throw new NotImplementedException();
	}

	@Override
	public String[] toRF2() throws Exception {
		if (additionalFields.size() != 1) {
			throw new IllegalStateException("Cannot yet determine column order for: " + this);
		}
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				(active?"1":"0"),
				moduleId, refsetId,
				referencedComponentId,
				additionalFields.values().iterator().next()
		};
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o instanceof RefsetMember) {
			return this.getId().equals(((RefsetMember)o).getId());
		}
		return false;
	}
	
	
	public String toString() {
		String additionalStr = "";
		boolean isFirst = true;
		for (String key : additionalFields.keySet()) {
			additionalStr += isFirst ? "" : ", ";
			additionalStr += key + "=" + additionalFields.get(key);
			isFirst = false;
		}
		String activeIndicator = isActive()?"":"*";
		return "[" + activeIndicator + "RM]:" + id + " - " + refsetId + " : " + referencedComponentId + " -> " + additionalStr;
	}

	/**
	 * @return true if both refset members have the same refsetId, referencedComponentId and additionalFields
	 */
	public boolean duplicates(RefsetMember that) {
		if (this.getRefsetId().equals(that.getRefsetId()) &&
				this.getReferencedComponentId().equals(that.getReferencedComponentId())) {
			return matchesAdditionalFields(that);
		}
		return false;
	}

	private boolean matchesAdditionalFields(RefsetMember that) {
		return this.getAdditionalFields().equals(that.getAdditionalFields());
	}

	public boolean hasAdditionalField(String key) {
		return getAdditionalFields().containsKey(key);
	}
}
