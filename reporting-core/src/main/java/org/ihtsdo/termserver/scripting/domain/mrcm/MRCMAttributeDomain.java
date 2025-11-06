package org.ihtsdo.termserver.scripting.domain.mrcm;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,domainId,grouped,attributeCardinality,attributeInGroupCardinality,ruleStrengthId,contentTypeId
public class MRCMAttributeDomain extends RefsetMember implements ScriptConstants {

	private static final String DOMAIN_ID = "domainId";
	private static final String GROUPED = "grouped";
	private static final String ATTRIBUTE_CARDINALITY = "attributeCardinality";
	private static final String IN_GROUP_CARDINALITY = "attributeInGroupCardinality";
	private static final String RULE_STRENGTH_ID = "ruleStrengthId";
	private static final String CONTENT_TYPE_ID = "contentTypeId";

	protected static final String[] additionalFieldNames = new String[]{DOMAIN_ID, GROUPED, ATTRIBUTE_CARDINALITY, IN_GROUP_CARDINALITY, RULE_STRENGTH_ID, CONTENT_TYPE_ID};

	public static MRCMAttributeDomain fromRf2(String[] lineItems) throws TermServerScriptException {
		MRCMAttributeDomain r = new MRCMAttributeDomain();
		populatefromRf2(r, lineItems, additionalFieldNames);
		return r;
	}

	@Override
	public MRCMAttributeDomain clone(String newComponentSctId, boolean keepIds) {
		return (MRCMAttributeDomain) populateClone(new MRCMAttributeDomain(), newComponentSctId, keepIds);
	}

	public String getDomainId() {
		return getField(DOMAIN_ID);
	}

	public void setDomainId(String domainId) {
		setField(DOMAIN_ID, domainId);
	}

	public String getGrouped() {
		return getField(GROUPED);
	}

	public void setGrouped(String constraint) {
		setField(GROUPED, constraint);
	}

	public String getAttributeCardinality() {
		return getField(ATTRIBUTE_CARDINALITY);
	}

	public void setAttributeCardinality(String rule) {
		setField(ATTRIBUTE_CARDINALITY, rule);
	}

	public String getInGroupCardinality() {
		return getField(IN_GROUP_CARDINALITY);
	}

	public void setInGroupCardinality(String rule) {
		setField(IN_GROUP_CARDINALITY, rule);
	}

	public String getRuleStrengthId() {
		return getField(RULE_STRENGTH_ID);
	}

	public void setRuleStrengthId(String rule) {
		setField(RULE_STRENGTH_ID, rule);
	}

	public String getContentTypeId() {
		return getField(CONTENT_TYPE_ID);
	}

	//Note that because Java does not support polymorphism of variables, only methods,
	//we need to call this method to pick up the field names of descendant types.
	@Override
	public String[] getAdditionalFieldNames() {
		return additionalFieldNames;
	}
}
