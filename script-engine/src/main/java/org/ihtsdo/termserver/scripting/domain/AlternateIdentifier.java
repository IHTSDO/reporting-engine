package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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
	
	public AlternateIdentifier() {}
	
	public String getAlternateIdentifier() {
		return alternateIdentifier;
	}

	public void setAlternateIdentifier(String alternateIdentifier) {
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
		return ComponentType.HISTORICAL_ASSOCIATION;
	}

	@Override
	public String[] toRF2() {
		String[] row = new String[6];
		int col = 0;
		row[col++] = getId();
		row[col++] = effectiveTime==null?"":effectiveTime;
		row[col++] = active?"1":"0";
		row[col++] = moduleId;
		row[col++] = identifierSchemeId;
		row[col++] = referencedComponentId;
		return row;
	}
	
	public static void populatefromRf2(AlternateIdentifier m, String[] lineItems, String[] additionalFieldNames) throws TermServerScriptException {
		m.setAlternateIdentifier(lineItems[REF_IDX_ID]);
		m.setEffectiveTime(lineItems[REF_IDX_EFFECTIVETIME]);
		m.setActive(lineItems[REF_IDX_ACTIVE].equals("1"));
		m.setModuleId(lineItems[REF_IDX_MODULEID]);
		m.setIdentifierSchemeId(lineItems[REF_IDX_REFSETID]);
		m.setReferencedComponentId(lineItems[REF_IDX_REFCOMPID]);
	}

	@Override 
	public boolean equals(Object o) {
		if (o instanceof AlternateIdentifier) {
			return this.getId().equals(((AlternateIdentifier)o).getId());
		}
		return false;
	}
	
	public String toString() {
		String activeIndicator = isActive()?"":"*";
		return  activeIndicator + alternateIdentifier + "(" + identifierSchemeId + ") --> " + referencedComponentId;
	}

	@Override
	public String getMutableFields() {
		String mutableFields = super.getMutableFields() + "," + this.referencedComponentId;
		return mutableFields;
	}
	
	public String toStringWithId() {
		return getId() + ": " + toString();
	}

	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
