package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,rangeConstraint,attributeRule,ruleStrengthId,contentTypeId
public class MRCMAttributeRange extends RefsetMember implements ScriptConstants {

	private static String RANGE_CONSTRAINT = "rangeConstraint";
	private static String ATTRIBUTE_RULE = "attributeRule";
	private static String RULE_STRENGTH_ID = "ruleStrengthId";
	private static String CONTENT_TYPE_ID = "contentTypeId";
	
	public static String[] additionalFieldNames = new String[] {RANGE_CONSTRAINT, ATTRIBUTE_RULE, RULE_STRENGTH_ID, CONTENT_TYPE_ID};
	
	public MRCMAttributeRange clone(String newComponentSctId) {
		MRCMAttributeRange clone = new MRCMAttributeRange();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = newComponentSctId;
		clone.setAdditionalFields(new HashMap<>(this.additionalFields));
		clone.isDirty = true; //New components need to be written to any delta
		return clone;
	}

	public static MRCMAttributeRange fromRf2(String[] lineItems) {
		MRCMAttributeRange r = new MRCMAttributeRange();
		populatefromRf2(r, lineItems, additionalFieldNames);
		return r;
	}
	
	public String getRangeConstraint() {
		return getField(RANGE_CONSTRAINT);
	}
	
	public void setRangeConstraint(String constraint) {
		setField(RANGE_CONSTRAINT, constraint);
	}
	
	public String getAttributeRule() {
		return getField(ATTRIBUTE_RULE);
	}
	
	public void setAttributeRule(String rule) {
		setField(ATTRIBUTE_RULE, rule);
	}
	
}
