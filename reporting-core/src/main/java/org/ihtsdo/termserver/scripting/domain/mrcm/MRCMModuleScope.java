package org.ihtsdo.termserver.scripting.domain.mrcm;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

//id,effectiveTime,active,moduleId,refsetId,referencedComponentId,mrcmRuleRefsetId
public class MRCMModuleScope extends RefsetMember implements ScriptConstants {

	public static final String RULE_REFSET_ID = "mrcmRuleRefsetId";

	protected static String[] additionalFieldNames = new String[] {RULE_REFSET_ID};

	@Override
	public MRCMModuleScope clone(String newComponentSctId, boolean keepIds) {
		return (MRCMModuleScope) super.populateClone(new MRCMModuleScope(), newComponentSctId, keepIds);
	}

	public static MRCMModuleScope fromRf2(String[] lineItems) throws TermServerScriptException {
		MRCMModuleScope r = new MRCMModuleScope();
		populatefromRf2(r, lineItems, additionalFieldNames);
		return r;
	}

	public String getRuleRefsetId() {
		return getField(RULE_REFSET_ID);
	}

	public void setRuleRefsetId(String ruleRefsetId) {
		setField(RULE_REFSET_ID, ruleRefsetId);
	}
	
	//Note that because Java does not support polymorphism of variables, only methods,
	//we need to call this method to pick up the field names of descendant types.
	@Override
	public String[] getAdditionalFieldNames() {
		return additionalFieldNames;
	}
	
}
