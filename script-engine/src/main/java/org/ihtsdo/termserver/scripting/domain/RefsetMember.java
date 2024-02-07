package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.TermServerScript;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class 

RefsetMember extends Component implements ScriptConstants {
	
	@SerializedName(value = "memberId", alternate = {"id"})
	@Expose
	protected String memberId;
	@SerializedName("refsetId")
	@Expose
	protected String refsetId;
	@SerializedName("referencedComponentId")
	@Expose
	protected String referencedComponentId;
	@SerializedName("additionalFields")
	@Expose
	protected Map<String, String> additionalFields = new HashMap<>();
	@SerializedName("referencedComponent")
	@Expose
	protected Object referencedComponent;
	
	protected String deletionEffectiveTime;
	
	protected boolean isDeleted = false;
	
	private static boolean firstFieldNamesWarningGiven = false;
	
	public RefsetMember() {}

	public RefsetMember(String id) {
		setId(id);
	}
	
	public String getMemberId() {
		return memberId;
	}
	
	public String getId() {
		if (id == null) {
			return memberId;
		}
		return id;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
		this.id = memberId;
	}

	public RefsetMember(String refsetId, Concept referencedCompoment, String[] additionalValues) {
		this.refsetId = refsetId;
		this.referencedComponent = referencedCompoment;
	}
	
	public boolean isDeleted() {
		return isDeleted;
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
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) throws TermServerScriptException {
		//TODO Add generic field comparison based off field names
		throw new NotImplementedException();
	}
	
	public void delete (String deletionEffectiveTime) {
		this.deletionEffectiveTime = deletionEffectiveTime;
		this.isDeleted = true;
	}
	
	public String[] toRF2Deletion() {
		throw new NotImplementedException();
	}

	@Override
	public String[] toRF2() {
		return toRF2(getAdditionalFieldNames());
	}
	
	public String[] toRF2(String[] additionalFieldNames) {
		//We're OK to output to RF2 without knowing exactly what this is as long as we have < 2
		//fields to output.  Otherwise we need to know the field names
		if (additionalFields.size() != additionalFieldNames.length) {
			//If additionalFields is just 1 then the order doens't matter, so we can warn and continue
			if (additionalFields.size() == 1) {
				additionalFieldNames = additionalFields.keySet().toArray(String[]::new);
				if (!firstFieldNamesWarningGiven) {
					TermServerScript.warn("toRF2() called without fieldName order being known, but only 1 field so doesn't matter");
					firstFieldNamesWarningGiven = true;
				}
			} else {
				throw new IllegalArgumentException("Additional field names supplied do not match data.");
			}
		}
		
		String[] row = new String[6 + additionalFieldNames.length];
		int col = 0;
		row[col++] = getId();
		row[col++] = effectiveTime==null?"":effectiveTime;
		row[col++] = active?"1":"0";
		row[col++] = moduleId;
		row[col++] = refsetId;
		row[col++] = referencedComponentId;
		
		for (String additionalFieldName : additionalFieldNames) {
			row[col++] = getField(additionalFieldName);
		}
		return row;
	}
	
	public static void populatefromRf2(RefsetMember m, String[] lineItems, String[] additionalFieldNames) throws TermServerScriptException {
		m.setId(lineItems[REF_IDX_ID]);
		m.setMemberId(lineItems[REF_IDX_ID]);
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
	
	public String[] getAdditionalFieldsArray() {
		String[] fields = new String[getAdditionalFieldNames().length];
		int col = 0;
		for (String additionalFieldName : getAdditionalFieldNames()) {
			fields[col++] = getField(additionalFieldName);
		}
		return fields;
	}
	
	//Note that because Java does not support polymorphism of variables, only methods,
	//we need to call this method to pick up the field names of descendant types.
	public String[] getAdditionalFieldNames() {
		return new String[0];
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o instanceof RefsetMember) {
			return this.getId().equals(((RefsetMember)o).getId());
		}
		return false;
	}
	
	public String toString() {
		return toString(false);
	}
	
	public String toString(boolean includeModuleId) {
		String additionalStr = "";
		boolean isFirst = true;
		for (String key : additionalFields.keySet()) {
			additionalStr += isFirst ? "" : ", ";
			additionalStr += key + "=" + additionalFields.get(key);
			isFirst = false;
		}
		String activeIndicator = isActive()?"":"*";
		String arrow = additionalStr.isEmpty() ? "" : " -> ";
		return "[" + activeIndicator + "RM]:" + getId() + " - " + refsetId + " : " + referencedComponentId + arrow + additionalStr + (includeModuleId ? " (Module: " + this.getModuleId() + ")" : "");
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

	public String getOnlyAdditionalFieldName() {
		if (additionalFields.size() != 1) {
			throw new IllegalArgumentException(this + " did not return only one additional field");
		}
		return additionalFields.keySet().iterator().next();
	}

	@Override
	public String getMutableFields() {
		String mutableFields = super.getMutableFields() + this.refsetId + "," 
				+ this.referencedComponentId;
		
		for (String additionalField : additionalFields.values()) {
			mutableFields += "," + additionalField;
		}
		return mutableFields;
	}
	
	public String toStringWithId() {
		return getId() + ": " + toString();
	}
}
