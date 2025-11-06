package org.ihtsdo.termserver.scripting.domain.mrcm;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,rangeConstraint,attributeRule,ruleStrengthId,contentTypeId
public class MRCMAttributeRange extends RefsetMember implements ScriptConstants {

	private static String RANGE_CONSTRAINT = "rangeConstraint";
	private static String ATTRIBUTE_RULE = "attributeRule";
	private static String RULE_STRENGTH_ID = "ruleStrengthId";
	private static String CONTENT_TYPE_ID = "contentTypeId";
	
	protected static String[] additionalFieldNames = new String[] {RANGE_CONSTRAINT, ATTRIBUTE_RULE, RULE_STRENGTH_ID, CONTENT_TYPE_ID};

	@Override
	public MRCMAttributeRange clone(String newComponentSctId, boolean keepIds) {
		return (MRCMAttributeRange) populateClone(new MRCMAttributeRange(), newComponentSctId, keepIds);
	}

	public static MRCMAttributeRange fromRf2(String[] lineItems) throws TermServerScriptException {
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
	
	public String getContentTypeId() {
		return getField(CONTENT_TYPE_ID);
	}
	
	//Note that because Java does not support polymorphism of variables, only methods,
	//we need to call this method to pick up the field names of descendant types.
	public String[] getAdditionalFieldNames() {
		return additionalFieldNames;
	}
}
