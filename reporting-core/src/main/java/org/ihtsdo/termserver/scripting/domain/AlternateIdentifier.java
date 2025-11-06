package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentStore;

public class 

AlternateIdentifier extends Component implements ScriptConstants {
	
	@SerializedName(value = "alternateIdentifier", alternate = {"id"})
	@Expose
	protected String alternateIdentifier;
	
	@SerializedName("identifierSchemeId")
	@Expose
	protected String identifierSchemeId;
	
	@SerializedName("referencedComponentId")
	@Expose
	protected String referencedComponentId;
	
	public String getAlternateIdentifier() {
		return alternateIdentifier;
	}

	public void setAlternateIdentifier(String alternateIdentifier) {
		this.alternateIdentifier = alternateIdentifier;
	}

	@Override
	public String getId() {
		return alternateIdentifier;
	}

	@Override
	public void setId(String alternateIdentifier) {
		this.alternateIdentifier = alternateIdentifier;
	}

	public String getIdentifierSchemeId() {
		return identifierSchemeId;
	}

	public void setIdentifierSchemeId(String identifierSchemeId) {
		this.identifierSchemeId = identifierSchemeId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public void setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
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
		return ComponentType.ALTERNATE_IDENTIFIER;
	}

	@Override
	public String[] toRF2() {
		String[] row = new String[6];
		int col = 0;
		row[col++] = getId();
		row[col++] = effectiveTime==null?"":effectiveTime;
		row[col++] = isActiveSafely()?"1":"0";
		row[col++] = moduleId;
		row[col++] = identifierSchemeId;
		row[col] = referencedComponentId;
		return row;
	}
	
	public static void populatefromRf2(AlternateIdentifier altId, String[] lineItems)  {
		altId.setAlternateIdentifier(lineItems[REF_IDX_ID]);
		altId.setEffectiveTime(lineItems[REF_IDX_EFFECTIVETIME]);
		altId.setActive(lineItems[REF_IDX_ACTIVE].equals("1"));
		altId.setModuleId(lineItems[REF_IDX_MODULEID]);
		altId.setIdentifierSchemeId(lineItems[REF_IDX_REFSETID]);
		altId.setReferencedComponentId(lineItems[REF_IDX_REFCOMPID]);
	}

	@Override 
	public boolean equals(Object o) {
		return (o instanceof AlternateIdentifier otherAltId)  && this.getId().equals(otherAltId.getId());
	}
	
	public String toString() {
		String activeIndicator = isActiveSafely()?"":"*";
		return  activeIndicator + alternateIdentifier + "(" + identifierSchemeId + ") --> " + referencedComponentId;
	}

	@Override
	public String[] getMutableFields() {
		String[] mutableFields = super.getMutableFields();
		int idx = super.getMutableFieldCount();
		mutableFields[idx] = this.identifierSchemeId;
		mutableFields[++idx] = this.referencedComponentId;
		return mutableFields;
	}

	@Override
	public int getMutableFieldCount() {
		return super.getMutableFieldCount() + 3;
	}

	@Override
	public String toStringWithId() {
		return getId() + ": " + toString();
	}

	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) throws TermServerScriptException {
		throw new NotImplementedException("TODO");
	}

	@Override
	public boolean matchesMutableFields(Component other) {
		AlternateIdentifier otherAltId = (AlternateIdentifier)other;
		return this.getIdentifierSchemeId().equals(otherAltId.getIdentifierSchemeId())
				&& this.getReferencedComponentId().equals(otherAltId.getReferencedComponentId());
	}

	@Override
	public List<Component> getReferencedComponents(ComponentStore cs) {
		return List.of(
				cs.getComponent(getReferencedComponentId()),
				cs.getComponent(getIdentifierSchemeId())
		);
	}
}
